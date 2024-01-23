package xyz.chener.ext.napt.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;


@EqualsAndHashCode(callSuper = false)
@TableName("client_item")
@Data
public class ClientItem extends Model<ClientItem> {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String clientUid;

    // 用于服务器请求的地址
    private String clientAddr;

    // 用于服务器被请求的端口
    private Integer serverPort;

    // 类型 tcp or  udp
    private Integer requestNtType;

    // 已使用流量 字节
    private Long flow;

    // 最大流量限制 字节   -1不限制
    private Long maxFlowLimit;

    // 速度限制  每秒多少字节  -1不限制
    private Long speedLimit;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    private String remark;



}
