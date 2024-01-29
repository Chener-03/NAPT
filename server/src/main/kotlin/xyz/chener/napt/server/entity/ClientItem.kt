package xyz.chener.napt.server.entity

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import java.util.*


@Entity
@Table(name = "client_item", indexes = [
    Index(name = "idx_client_uid", columnList = "client_uid", unique = false)
])
open class ClientItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    open var id: Long? = null

    @Column(name = "client_uid", length = 255)
    @NotNull
    open var clientUid: String? = null

    // 用于服务器请求的地址
    @Column(name = "client_addr", length = 255)
    @NotNull
    open var clientAddr: String? = null

    // 用于服务器被请求的端口
    @Column(name = "server_port")
    @NotNull
    open var serverPort: Int? = null

    // 类型 tcp or  udp
    @Column(name = "client_nt_type")
    @NotNull
    open var requestNtType: Int? = null

    // 已使用流量 字节
    @Column(name = "flow")
    @NotNull
    open var flow: Long? = null

    // 最大流量限制 字节   -1不限制
    @Column(name = "max_flow_limit")
    @NotNull
    open var maxFlowLimit: Long? = null

    // 速度限制  每秒多少字节  -1不限制
    @Column(name = "speed_limit")
    @NotNull
    open var speedLimit: Long? = null

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Column(name = "create_time")
    open var createTime: Date? = null

    @Column(name = "remark", length = 255)
    open var remark: String? = null


}