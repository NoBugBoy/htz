namespace TradeService.Common;

@doc("全局统一异常返回体")
@error
model ProblemDetail {
   @doc("唯一限定符")
   type: string;
   @doc("错误头")
   title: string;
   @doc("错误详情（要toast的内容)")
   detail: string;
   @doc("状态码")
   status: int32;
   @doc("附加信息 Map<String, object>")
   properties : Record<unknown>;
}






