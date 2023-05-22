package xyz.chener.ext.napt.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;


@TableName("client_item")
public class ClientItem extends Model<ClientItem> {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String clientUid;

    // 用于服务器请求的地址
    private String clientAddr;

    // 用于服务器被请求的端口
    private Integer serverPort;

    // 已使用流量 字节
    private Long flow;

    // 最大流量限制 字节   -1不限制
    private Long maxFlowLimit;

    // 速度限制  每秒多少字节  -1不限制
    private Long speedLimit;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    private String remark;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getClientUid() {
        return clientUid;
    }

    public void setClientUid(String clientUid) {
        this.clientUid = clientUid;
    }

    public String getClientAddr() {
        return clientAddr;
    }

    public void setClientAddr(String clientAddr) {
        this.clientAddr = clientAddr;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public Long getFlow() {
        return flow;
    }

    public void setFlow(Long flow) {
        this.flow = flow;
    }

    public Long getMaxFlowLimit() {
        return maxFlowLimit;
    }

    public void setMaxFlowLimit(Long maxFlowLimit) {
        this.maxFlowLimit = maxFlowLimit;
    }

    public Long getSpeedLimit() {
        return speedLimit;
    }

    public void setSpeedLimit(Long speedLimit) {
        this.speedLimit = speedLimit;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
