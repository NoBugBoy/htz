package com.knowflow.application.document.model.event;

/**
 * 文档查阅领域事件（用于异步累计阅读计数或统计审计）
 */
public record DocumentReadEvent(Long documentId) {}
