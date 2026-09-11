import "@typespec/http";
import "../base/base.tsp";

using TradeService.Common;
using TypeSpec.Http;

model AdminCreateRequest{
    email: string;
    password: string;
}

@route("/admin")
interface adminInterface{
    @route("/create")
    createAdmin(@body req: AdminCreateRequest): void | ProblemDetail

}

