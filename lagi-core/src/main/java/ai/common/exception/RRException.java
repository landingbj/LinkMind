package ai.common.exception;

import lombok.Data;

/**
 * @program: RRException
 * @description: restful response exception
 * @author: linzhen
 * @create: 2023-06-29 09:00
 **/
@Data
public class RRException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private int code = 500;
    private String msg;

    public RRException() {
        super();
    }

    public RRException(String msg) {
        super(msg);
        this.msg = msg;
    }

    public RRException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }
}

