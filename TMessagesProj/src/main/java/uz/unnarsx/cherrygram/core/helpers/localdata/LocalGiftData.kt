/**
 * 爱琦艺客户端 - 本地虚拟礼物数据模型
 *
 * 纯本地存储，不会上传服务器，也不产生任何真实订单。
 * 字段设计参考黄昏 LocalProfileGiftData，保留兼容所需的全部展示信息。
 *
 * @author Aiqiyi
 */
package uz.unnarsx.cherrygram.core.helpers.localdata

import com.google.gson.annotations.SerializedName

data class LocalGiftData(

    /** 礼物唯一 collectibleId（本地生成，保持全局唯一，避免和真实礼物冲突） */
    @SerializedName("collectibleId")
    var collectibleId: Long = 0L,

    /** 礼物图案 Document id */
    @SerializedName("documentId")
    var documentId: Long = 0L,

    /** 礼物标题 */
    @SerializedName("title")
    var title: String? = null,

    /** 礼物 slug（唯一标识名） */
    @SerializedName("slug")
    var slug: String? = null,

    /** 图案 Document id（渐变/纹理） */
    @SerializedName("patternDocumentId")
    var patternDocumentId: Long = 0L,

    /** 中心色 */
    @SerializedName("centerColor")
    var centerColor: Int = -0xb47016,

    /** 边缘色 */
    @SerializedName("edgeColor")
    var edgeColor: Int = -0xd89942,

    /** 图案色 */
    @SerializedName("patternColor")
    var patternColor: Int = -0x462701,

    /** 文字色 */
    @SerializedName("textColor")
    var textColor: Int = -0x1,

    /** 是否为本地自定义样式（true 时颜色三件套优先生效） */
    @SerializedName("localStyle")
    var localStyle: Boolean = false,

    /** 发送者显示名 */
    @SerializedName("senderName")
    var senderName: String? = null,

    /** 礼物目录 id（对应真实礼物则填真实 id，纯虚拟填 0） */
    @SerializedName("catalogGiftId")
    var catalogGiftId: Long = 0L,

    /** 数量 */
    @SerializedName("giftNum")
    var giftNum: Int = 1,

    /** 模型名（3D 模型/贴图名） */
    @SerializedName("modelName")
    var modelName: String? = null,

    /** 图案名 */
    @SerializedName("patternName")
    var patternName: String? = null,

    /** 背景名 */
    @SerializedName("backdropName")
    var backdropName: String? = null,

    /** 面值金额 */
    @SerializedName("valueAmount")
    var valueAmount: Long = 0L,

    /** 面值货币 */
    @SerializedName("valueCurrency")
    var valueCurrency: String? = null,

    /** 面值折合 USD 分 */
    @SerializedName("valueUsdAmount")
    var valueUsdAmount: Long = 0L,

    /** 已发行数量 */
    @SerializedName("availabilityIssued")
    var availabilityIssued: Int = 0,

    /** 总发行量 */
    @SerializedName("availabilityTotal")
    var availabilityTotal: Int = 0,

    /** 原始发送者 id */
    @SerializedName("originalSenderId")
    var originalSenderId: Long = 0L,

    /** 原始接收者 id */
    @SerializedName("originalRecipientId")
    var originalRecipientId: Long = 0L,

    /** 原始日期（unix 秒） */
    @SerializedName("originalDate")
    var originalDate: Int = 0,

    /** 原始留言 */
    @SerializedName("originalMessage")
    var originalMessage: String? = null,

    /** 图案 Document 的序列化数据（base64，用于本地还原图案） */
    @SerializedName("patternDocumentData")
    var patternDocumentData: String? = null,
    /**
     * 爱琦艺：是否已佩戴。
     *
     * true  = 显示在资料页名字下方（用户点佩戴后置为 true）
     * false = 只在礼物列表里躺着，不参与资料页绘制
     *
     * 违规礼物佩戴入口写入时为 true（直接挂上，不需手动点）。
     * 典藏购买/送礼写入时为 false，等用户在图一/图二点佩戴。
     */
    @SerializedName("pinned")
    var pinned: Boolean = false,

    /**
     * 爱琦艺：自定义典藏编号。
     *
     * 0  = 未修改，显示礼物原本的编号（沿用 giftNum）
     * >0 = 用户手动指定的编号，覆盖 giftNum 参与所有 #N / 典藏 N 的渲染
     *
     * 只影响本机显示，不上传服务器，不影响别人看到的样子。
     */
    @SerializedName("customNum")
    var customNum: Int = 0

) {
    companion object {
        /** 默认中心色 */
        const val DEFAULT_CENTER_COLOR: Int = -0xb47016

        /** 默认边缘色 */
        const val DEFAULT_EDGE_COLOR: Int = -0xd89942

        /** 默认图案色 */
        const val DEFAULT_PATTERN_COLOR: Int = -0x462701

        /** 默认文字色（白） */
        const val DEFAULT_TEXT_COLOR: Int = -0x1
    }
}
