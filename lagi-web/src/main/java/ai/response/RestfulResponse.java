package ai.response;

import java.io.Serializable;

/**
 * @program: RestfulResponse
 *
 * @description: restful response
 *
 * @author: linzhen
 *
 * @create: 2023-06-29 09:00
 **/
public class RestfulResponse <T> implements Serializable{

	private static final long serialVersionUID = 1L;

	private Integer code;
	private String message;
	private T data;
	private String errorMsg;
	public Integer getCode() {
		return code;
	}
	public void setCode(Integer code) {
		this.code = code;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public T getData() {
		return data;
	}
	public void setData(T data) {
		this.data = data;
	}
	public String getErrorMsg() {
		return errorMsg;
	}
	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}


	public static RestfulResponse<Object> error(String errorMsg) {
		return error(500, errorMsg);
	}

	public static RestfulResponse<Object> error(int code , String errorMsg) {
		RestfulResponse<Object> response = new RestfulResponse<>();
		response.setCode(code);
		response.setErrorMsg(errorMsg);
		response.setMessage("failed");
		return response;
	}

	public static <T>RestfulResponse<T> sucecced(T data) {
		RestfulResponse<T> response = new RestfulResponse<>();
		response.setCode(0);
		response.setData(data);
		response.setMessage("success");
		return response;
	}
}
