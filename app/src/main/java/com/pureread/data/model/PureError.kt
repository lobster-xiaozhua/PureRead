package com.pureread.data.model

/**
 * PureRead 业务异常密封类。
 *
 * 职责：
 * - 将所有异常统一分层为网络、正文提取、资源不存在、未知等类型
 * - 替代直接抛出异常，便于 UI 层做差异化提示
 *
 * 线程安全：不可变数据类，线程安全。
 */
public sealed class PureError {

    /**
     * 网络层异常。
     *
     * @param throwable       原始异常，可能为 null
     * @param messageString   业务描述
     */
    public data class Network(
        public val throwable: Throwable? = null,
        public val messageString: String = ""
    ) : PureError() {
        public constructor(messageString: String) : this(null, messageString)
    }

    /**
     * 正文提取异常。
     *
     * @param throwable       原始异常，可能为 null
     * @param messageString   业务描述
     */
    public data class Extract(
        public val throwable: Throwable? = null,
        public val messageString: String = ""
    ) : PureError() {
        public constructor(messageString: String) : this(null, messageString)
    }

    /**
     * 资源不存在异常。
     *
     * @param resourceNameString 资源标识名
     */
    public data class NotFound(
        public val resourceNameString: String
    ) : PureError()

    /**
     * 数据库/存储异常。
     *
     * @param throwable       原始异常，可能为 null
     * @param messageString   业务描述
     */
    public data class Storage(
        public val throwable: Throwable? = null,
        public val messageString: String = ""
    ) : PureError() {
        public constructor(messageString: String) : this(null, messageString)
    }

    /**
     * 未知异常。
     *
     * @param throwable       原始异常，可能为 null
     * @param messageString   业务描述
     */
    public data class Unknown(
        public val throwable: Throwable? = null,
        public val messageString: String = ""
    ) : PureError() {
        public constructor(messageString: String) : this(null, messageString)
    }

    /**
     * 致命异常（兼容旧代码，映射到未知异常）。
     *
     * @param throwable 原始异常
     */
    public data class Fatal(
        public val throwable: Throwable
    ) : PureError()

    /**
     * 服务端/后端异常（兼容旧代码，映射到网络异常）。
     *
     * @param messageString 业务描述
     */
    public data class Server(
        public val messageString: String
    ) : PureError()

    /**
     * 业务异常（兼容旧代码，映射到提取异常）。
     *
     * @param messageString 业务描述
     */
    public data class Business(
        public val messageString: String
    ) : PureError()
}
