package com.novel.book.mq;

import com.novel.common.constant.ErrorCodeEnum;
import lombok.Getter;

/**
 * 书籍服务事务消息 {@code executeLocalTransaction} 中业务失败时抛出，映射为回滚半消息。
 */
@Getter
public class BookLocalTxAbortException extends RuntimeException {

    private final ErrorCodeEnum errorCode;

    public BookLocalTxAbortException(ErrorCodeEnum errorCode) {
        super(errorCode != null ? errorCode.getMessage() : "tx abort");
        this.errorCode = errorCode;
    }

    public BookLocalTxAbortException(ErrorCodeEnum errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
