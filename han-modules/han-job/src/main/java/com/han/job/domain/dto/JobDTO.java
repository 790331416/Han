package com.han.job.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.han.job.domain.po.SysJobPo;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务创建/更新 DTO，兼容扁平 JSON 与内部组合结构。
 */
@Data
public class JobDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private SysJobPo base;

    @JsonIgnore
    public SysJobPo getBase() {
        return base;
    }

    @JsonIgnore
    public void setBase(SysJobPo base) {
        this.base = base;
    }

    @JsonProperty("jobId")
    public Long getJobId() {
        return base != null ? base.getJobId() : null;
    }

    @JsonProperty("jobId")
    public void setJobId(Long jobId) {
        ensureBase().setJobId(jobId);
    }

    @JsonProperty("jobName")
    public String getJobName() {
        return base != null ? base.getJobName() : null;
    }

    @JsonProperty("jobName")
    public void setJobName(String jobName) {
        ensureBase().setJobName(jobName);
    }

    @JsonProperty("jobGroup")
    public String getJobGroup() {
        return base != null ? base.getJobGroup() : null;
    }

    @JsonProperty("jobGroup")
    public void setJobGroup(String jobGroup) {
        ensureBase().setJobGroup(jobGroup);
    }

    @JsonProperty("invokeTarget")
    public String getInvokeTarget() {
        return base != null ? base.getInvokeTarget() : null;
    }

    @JsonProperty("invokeTarget")
    public void setInvokeTarget(String invokeTarget) {
        ensureBase().setInvokeTarget(invokeTarget);
    }

    @JsonProperty("cronExpression")
    public String getCronExpression() {
        return base != null ? base.getCronExpression() : null;
    }

    @JsonProperty("cronExpression")
    public void setCronExpression(String cronExpression) {
        ensureBase().setCronExpression(cronExpression);
    }

    @JsonProperty("misfirePolicy")
    public String getMisfirePolicy() {
        return base != null ? base.getMisfirePolicy() : null;
    }

    @JsonProperty("misfirePolicy")
    public void setMisfirePolicy(String misfirePolicy) {
        ensureBase().setMisfirePolicy(misfirePolicy);
    }

    @JsonProperty("concurrent")
    public String getConcurrent() {
        return base != null ? base.getConcurrent() : null;
    }

    @JsonProperty("concurrent")
    public void setConcurrent(String concurrent) {
        ensureBase().setConcurrent(concurrent);
    }

    @JsonProperty("status")
    public String getStatus() {
        return base != null ? base.getStatus() : null;
    }

    @JsonProperty("status")
    public void setStatus(String status) {
        ensureBase().setStatus(status);
    }

    @JsonProperty("remark")
    public String getRemark() {
        return base != null ? base.getRemark() : null;
    }

    @JsonProperty("remark")
    public void setRemark(String remark) {
        ensureBase().setRemark(remark);
    }

    @JsonProperty("createBy")
    public String getCreateBy() {
        return base != null ? base.getCreateBy() : null;
    }

    @JsonProperty("createBy")
    public void setCreateBy(String createBy) {
        ensureBase().setCreateBy(createBy);
    }

    @JsonProperty("createTime")
    public LocalDateTime getCreateTime() {
        return base != null ? base.getCreateTime() : null;
    }

    @JsonProperty("createTime")
    public void setCreateTime(LocalDateTime createTime) {
        ensureBase().setCreateTime(createTime);
    }

    @JsonProperty("updateBy")
    public String getUpdateBy() {
        return base != null ? base.getUpdateBy() : null;
    }

    @JsonProperty("updateBy")
    public void setUpdateBy(String updateBy) {
        ensureBase().setUpdateBy(updateBy);
    }

    @JsonProperty("updateTime")
    public LocalDateTime getUpdateTime() {
        return base != null ? base.getUpdateTime() : null;
    }

    @JsonProperty("updateTime")
    public void setUpdateTime(LocalDateTime updateTime) {
        ensureBase().setUpdateTime(updateTime);
    }

    private SysJobPo ensureBase() {
        if (base == null) {
            base = new SysJobPo();
        }
        return base;
    }
}
