//package ai.llm.utils.convert;
//
//import ai.common.exception.RRException;
//import ai.llm.utils.LLMErrorConstants;
//import com.zhipu.oapi.service.v4.model.ModelApiResponse;
//
//public class ZhiPuConvert {
//    //    200	业务处理成功	-
//    //    400	参数错误	检查接口参数是否正确
//    //    400	文件内容异常	检查jsonl文件内容是否符合要求
//    //    401	鉴权失败或Token超时	确认API KEY和鉴权token是否正确生成
//    //    404	微调功能未开放	联系客服以开通此功能
//    //    404	微调任务不存在	确保微调任务ID正确
//    //    429	接口请求并发超额	调整请求频率或联系商务扩大并发数
//    //    429	上传文件频率过快	短暂等待后重新请求
//    //    429	账户余额已用完	进行账户充值以确保余额充足
//    //    429	账户异常	账户存违规行为，请联系平台客服或service@zhipuai.cn解除相关锁定
//    //    429	终端账号异常	终端用户存在违规行为，账号已被锁定
//    //    434	暂无API权限，微调API及文件管理API为内测阶段，我们会尽快开放	等待接口正式开放或请联系平台客服申请内测
//    //    435	文件大小超过100MB	使用小于100MB的jsonl文件或分批上传
//    //    500	服务器处理请求时发生错误	稍后重试或联系客服
//    public static RRException convert2RRException(ModelApiResponse response) {
//        int code = response.getCode();
//        String msg = response.getMsg();
//        if(400 == code) {
//            return new RRException(LLMErrorConstants.INVALID_REQUEST_ERROR, msg);
//        }
//        if(401 == code) {
//            return new RRException(LLMErrorConstants.INVALID_AUTHENTICATION_ERROR, msg);
//        }
//        if(404 == code || 434 == code || 435 == code) {
//            return new RRException(LLMErrorConstants.OTHER_ERROR, msg);
//        }
//        if(429 == code) {
//            if(msg.contains("账户")) {
//                return new RRException(LLMErrorConstants.PERMISSION_DENIED_ERROR, msg);
//            }
//            if(msg.contains("接口")) {
//                return new RRException(LLMErrorConstants.RATE_LIMIT_REACHED_ERROR, msg);
//            }
//        }
//        if(500 == code) {
//            return new RRException(LLMErrorConstants.SERVER_ERROR, msg);
//        }
//        return new RRException(LLMErrorConstants.OTHER_ERROR, msg);
//    }
//}
