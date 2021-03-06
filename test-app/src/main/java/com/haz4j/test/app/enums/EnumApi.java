package com.haz4j.test.app.enums;

import com.googlecode.jsonrpc4j.JsonRpcParam;
import com.googlecode.jsonrpc4j.JsonRpcService;
import com.haz4j.swagger.annotation.Api;
import com.haz4j.swagger.annotation.ApiOperation;


@JsonRpcService("/v1/enums")
@Api(tags = "enums", value = "Api enums")
public interface EnumApi {

    @ApiOperation(value = "Save entity")
    EnumDto save(@JsonRpcParam(value = "enum_dto") EnumDto enumDto);

}