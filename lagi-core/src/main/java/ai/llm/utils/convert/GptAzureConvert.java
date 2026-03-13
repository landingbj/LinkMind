package ai.llm.utils.convert;

import ai.common.exception.RRException;
import ai.llm.utils.LLMErrorConstants;
import ai.openai.pojo.ChatCompletionResult;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

@Slf4j
public class GptAzureConvert {
    private static final Logger logger = LoggerFactory.getLogger(GptAzureConvert.class);
    private static final String PROXY_URL = "proxy.digimeta.com.cn";
    private static final Integer PROXY_PORT = 18888;
    private static final Gson gson = new Gson();
    public static int convert(Object object) {
        if(object instanceof  Integer) {
            return convertByInt((int) object);
        }
        if(object instanceof Response) {
            return convertByResponse((Response) object);
        }
        return LLMErrorConstants.OTHER_ERROR;
    }

//    NoAuthenticationInformation	未授权 (401)	服务器无法验证该请求。 请参阅 www-authenticate 标头中的信息。
//    InvalidAuthenticationInfo	未授权 (401)	服务器无法验证该请求。 请参阅 www-authenticate 标头中的信息。
//    AccountAlreadyExists	冲突 (409)	指定的帐户已存在。
//    AccountBeingCreated	冲突 (409)	正在创建指定的帐户。
//    AccountIsDisabled	禁止访问 (403)	已禁用指定的帐户。
//    AccountProtectedFromDeletion	冲突 (409)	帐户 <accountName> 容器 <containerName> 具有 <have legal hold\locked immutability policy>。
//    AuthenticationFailed	禁止访问 (403)	服务器无法验证该请求。 请确保 Authorization 标头的值构成正确，包括签名。
//    ConditionHeadersNotSupported	BadRequest (400)	不支持条件标头。
//    ConditionNotMet	未修改 (304)	不符合在条件标头中指定的条件，无法执行读取操作。
//    ConditionNotMet	前提条件失败 (412)	不符合在条件标头中指定的条件，无法执行写入操作。
//    EmptyMetadataKey	错误的请求 (400)	元数据键-值对之一的键是空的。
//    InsufficientAccountPermissions	禁止访问 (403)	读取操作当前处于禁用状态。
//    InsufficientAccountPermissions	禁止访问 (403)	不允许写入操作。
//    InsufficientAccountPermissions	禁止访问 (403)	正在访问的帐户没有足够的权限来执行此操作。
//    InternalError	内部服务器错误 (500)	服务器遇到内部错误。 请重试请求。
//    InvalidAuthenticationInfo	错误的请求 (400)	未以正确的格式提供身份验证信息。 验证 Authorization 标头的值。
//    InvalidHeaderValue	错误的请求 (400)	为 HTTP 标头之一提供的值的格式不正确。
//    InvalidHttpVerb	错误的请求 (400)	服务器无法识别指定的 HTTP 动词。
//    InvalidInput	错误的请求 (400)	某个请求输入无效。
//    InvalidMd5	错误的请求 (400)	在请求中指定的 MD5 值无效。 MD5 值必须为 128 位并采用 Base64 编码。
//    InvalidMetadata	错误的请求 (400)	指定的元数据无效。 它包括不允许的字符。
//    InvalidQueryParameterValue	错误的请求 (400)	为请求 URI 中的查询参数之一指定的值无效。
//    InvalidRange	请求的范围不符合条件 (416)	指定的范围对当前资源大小无效。
//    InvalidResourceName	错误的请求 (400)	指定的资源名称包含无效字符。
//    InvalidUri	错误的请求 (400)	请求的 URI 不表示服务器上的任何资源。
//    InvalidXmlDocument	错误的请求 (400)	指定的 XML 语法无效。
//    InvalidXmlNodeValue	错误的请求 (400)	为请求正文中的 XML 节点之一提供的值的格式不正确。
//    KeyVaultAccessTokenCannotBeAcquired	禁止访问 (403)	无法使用此资源的标识从 Microsoft Entra 获取密钥保管库的访问令牌。
//    KeyVaultEncryptionKeyNotFound	禁止访问 (403)	找不到密钥保管库密钥来解包加密密钥。
//    KeyVaultVaultNotFound	禁止访问 (403)	找不到密钥保管库保管库。
//    Md5Mismatch	错误的请求 (400)	在请求中指定的 MD5 值与服务器计算的 MD5 值不匹配。
//    MetadataTooLarge	错误的请求 (400)	指定的元数据大小超过允许的最大大小。
//    MissingContentLengthHeader	需要指定长度 (411)	未指定 Content-Length 标头。
//    MissingRequiredQueryParameter	错误的请求 (400)	没有为此请求指定所需的查询参数。
//    MissingRequiredHeader	错误的请求 (400)	未指定所需的 HTTP 标头。
//    MissingRequiredXmlNode	错误的请求 (400)	请求正文中未指定所需的 XML 节点。
//    MultipleConditionHeadersNotSupported	错误的请求 (400)	不支持多个条件标头。
//    OperationTimedOut	内部服务器错误 (500)	无法在允许的时间内完成该操作。 该操作可能在服务器端成功，也可能未成功。 请在重试操作之前查询服务器状态。
//    OutOfRangeInput	错误的请求 (400)	请求输入之一超过范围。
//    OutOfRangeQueryParameterValue	错误的请求 (400)	在请求 URI 中指定的查询参数超过了允许范围。
//    RequestBodyTooLarge	请求实体太大 (413)	请求正文大小超过允许的最大大小。
//    ResourceTypeMismatch	冲突 (409)	指定的资源类型与现有资源的类型不匹配。
//    RequestUrlFailedToParse	错误的请求 (400)	无法解析请求中的 URL。
//    ResourceAlreadyExists	冲突 (409)	指定的资源已存在。
//    ResourceNotFound	找不到 (404)	指定的资源不存在。
//    ServerBusy	服务不可用 (503)	服务器当前无法接收请求。 请重试请求。
//    ServerBusy	服务不可用 (503)	入口超出帐户限制。
//    ServerBusy	服务不可用 (503)	出口超出帐户限制。
//    ServerBusy	服务不可用 (503)	每秒操作数超过帐户限制。
//    UnsupportedHeader	错误的请求 (400)	不支持请求中指定的 HTTP 标头之一。
//    UnsupportedXmlNode	错误的请求 (400)	不支持请求正文中指定的 XML 节点之一。
//    UnsupportedQueryParameter	错误的请求 (400)	不支持请求 URI 中指定的查询参数之一。
//    UnsupportedHttpVerb	不允许使用方法 (405)	资源不支持指定的 HTTP 动词。

    public static int convertByInt(int errorCode) {
        if(errorCode == 400 || 405 == errorCode) {
            return LLMErrorConstants.INVALID_REQUEST_ERROR;
        }
        if(errorCode == 401 || 409 == errorCode) {
            return LLMErrorConstants.INVALID_AUTHENTICATION_ERROR;
        }
        if(errorCode == 403) {
            return LLMErrorConstants.PERMISSION_DENIED_ERROR;
        }
        if(errorCode == 404) {
            return LLMErrorConstants.RESOURCE_NOT_FOUND_ERROR;
        }
        if(errorCode == 503) {
            return LLMErrorConstants.RATE_LIMIT_REACHED_ERROR;
        }
        if(errorCode == 500) {
            return LLMErrorConstants.SERVER_ERROR;
        }
        return LLMErrorConstants.OTHER_ERROR;
    }

    public static ChatCompletionResult convert2ChatCompletionResult(String body) {
        if(body == null) {
            return null;
        }
        return gson.fromJson(body, ChatCompletionResult.class);
    }

    public static ChatCompletionResult convertStreamLine2ChatCompletionResult(String body) {
        // 记录日志
        if (body.equals("[DONE]")) {
            return null;
        }
        ChatCompletionResult result = gson.fromJson(body, ChatCompletionResult.class);
        result.getChoices().forEach(choice -> {
            choice.setMessage(choice.getDelta());
//            choice.setDelta(null);
        });
        return result;
    }

    public static InetSocketAddress convertProxyUrl2InetSocketAddress() {
        return new InetSocketAddress(PROXY_URL, PROXY_PORT);
    }

    public static int convertByResponse(Response response) {
        return convert(response.code());
    }

    public static RRException convert2RResponse(Response response) {
        return new RRException(convertByResponse(response), response.message());
    }
}
