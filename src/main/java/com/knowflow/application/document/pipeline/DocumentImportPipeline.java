package com.knowflow.application.document.pipeline;

import com.knowflow.application.common.ErrorCode;
import com.knowflow.application.common.storage.FileStorageGateway;
import com.knowflow.application.document.model.entity.DocSourceFileEntity;
import com.knowflow.application.document.model.entity.DocumentEntity;
import com.knowflow.application.document.parser.DocParseCommand;
import com.knowflow.application.document.parser.DocParseResult;
import com.knowflow.application.document.parser.DocParserEngineEnum;
import com.knowflow.application.document.parser.DocParserGateway;
import com.knowflow.application.document.repository.DocSourceFileRepository;
import com.knowflow.application.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 多源文件导入管线编排服务 (Document Import Pipeline) 遵循“IO 操作移出事务”与“先存储后解析”工程规范： 1. 上传至 rustfs 对象存储底座并计算
 * SHA-256 (支持秒传去重)； 2. 独立小事务：来源文件资产入库 (DocSourceFileEntity)； 3. 路由至统一文档解析网关 (DocParserGateway)，仅透传
 * rustfs 存储地址与格式元数据； 4. 独立小事务：持久化生成 DocumentEntity 主文档并建立溯源双向绑定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentImportPipeline {

  private final FileStorageGateway fileStorageGateway;
  private final DocParserGateway docParserGateway;
  private final DocSourceFileRepository docSourceFileRepository;
  private final DocumentImportTransactionalService transactionalService;

  /** 核心导入入口方法 (编排入口，不加全局 @Transactional，避免长事务包裹文件 IO) */
  public DocumentImportResult importFile(DocumentImportCommand command) {
    String fileName = command.originalFileName();

    log.info(
        "【DocumentImportPipeline】开始执行导入管线: workspaceId={}, fileName={}, size={}",
        command.workSpaceId(),
        fileName,
        command.fileSize());

    // 1. 计算 SHA-256 哈希值 (JDK 标准库无额外依赖)
    String fileHash = calculateSha256(command.fileBytes());

    // 2. 探测工作空间内是否存在同哈希文件（支持秒传与对象存储复用）
    Optional<DocSourceFileEntity> existingOpt =
        docSourceFileRepository.findByWorkSpaceIdAndFileHash(command.workSpaceId(), fileHash);

    String storagePath;
    String storageUrl;
    boolean instantUpload = false;

    if (existingOpt.isPresent()) {
      // 用 map 提取避免直接 .get()，orElseThrow 理论不可达（isPresent 已确认）
      storagePath = existingOpt.map(DocSourceFileEntity::getStoragePath).orElseThrow();
      storageUrl = existingOpt.map(DocSourceFileEntity::getStorageUrl).orElseThrow();
      instantUpload = true;
      log.info(
          "【DocumentImportPipeline】命中文件哈希秒传去重: hash={}, storagePath={}", fileHash, storagePath);
    } else {
      // 3. 上传到 rustfs 存储底座（耗时网络 IO，置于事务外）
      storagePath =
          String.format(
              "workspaces/%d/docs/%s_%s",
              command.workSpaceId(),
              fileHash.substring(0, Math.min(16, fileHash.length())),
              fileName);

      try (ByteArrayInputStream in = new ByteArrayInputStream(command.fileBytes())) {
        storageUrl =
            fileStorageGateway.upload(storagePath, in, command.contentType(), command.fileSize());
        log.info("【DocumentImportPipeline】文件成功落地 rustfs: path={}", storagePath);
      } catch (Exception e) {
        log.error("【DocumentImportPipeline】文件上传至 rustfs 失败: path={}", storagePath, e);
        throw new BusinessException(
            ErrorCode.Document.FILE_STORAGE_ERROR, "文件存储失败: " + e.getMessage());
      }
    }

    // 4. 落库保存来源文件资产初始记录 (委托独立事务服务)
    DocSourceFileEntity sourceFile =
        transactionalService.saveInitialSourceFile(command, fileHash, storagePath, storageUrl);

    // 5. 调用通用解析网关 (仅透传存储路径与格式，不透传字节流，置于事务外)
    Map<String, Object> extraOptions = new HashMap<>(command.extraOptions());
    if (command.extractPdfToDoc()) {
      extraOptions.put("ocr", true);
    }

    DocParseCommand parseCmd =
        DocParseCommand.of(
            null, // 字节流置空，强制解耦使用 rustfs 存储寻址
            storagePath,
            fileName,
            command.contentType(),
            command.preferredEngine(),
            extraOptions);

    DocParseResult parseResult = docParserGateway.parse(parseCmd);

    // 6. 根据解析策略完成业务主文档生成与关联绑定 (委托独立事务服务)
    return transactionalService.finalizeImport(
        command, sourceFile.getId(), storagePath, storageUrl, instantUpload, parseResult);
  }

  /** PDF 一键提炼为可编辑在线文档用例 */
  public DocumentEntity extractPdfToDocument(
      Long sourceFileId, Long targetCategoryId, Long operatorId) {
    DocSourceFileEntity sourceFile =
        docSourceFileRepository
            .findById(sourceFileId)
            .orElseThrow(() -> new BusinessException(ErrorCode.Document.DOC_NOT_FOUND, "来源文件不存在"));

    if (!"pdf".equalsIgnoreCase(sourceFile.getFileExt())) {
      throw new BusinessException(ErrorCode.Document.UNSUPPORTED_DOC_TYPE, "仅支持对 PDF 文件进行提炼转换");
    }

    log.info(
        "【DocumentImportPipeline】触发 PDF 一键 AI 提炼: sourceFileId={}, operatorId={}",
        sourceFileId,
        operatorId);

    // 构造带 OCR 的提取命令
    DocParseCommand extractCmd =
        DocParseCommand.of(
            null,
            sourceFile.getStoragePath(),
            sourceFile.getOriginalFileName(),
            sourceFile.getMimeType(),
            DocParserEngineEnum.AUTO,
            Map.of("ocr", true));

    DocParseResult extractResult = docParserGateway.parse(extractCmd);
    if (!extractResult.isSuccess()) {
      throw new BusinessException(
          ErrorCode.Document.DOC_PARSE_FAILED, "PDF 提炼失败: " + extractResult.errorMessage());
    }

    return transactionalService.saveExtractedPdfDocument(
        sourceFile, targetCategoryId, extractResult);
  }

  /** 兼容旧签名的重载方法 */
  public DocumentEntity extractPdfToDocument(Long sourceFileId, Long targetCategoryId) {
    return extractPdfToDocument(sourceFileId, targetCategoryId, null);
  }

  private static String calculateSha256(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(bytes);
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
