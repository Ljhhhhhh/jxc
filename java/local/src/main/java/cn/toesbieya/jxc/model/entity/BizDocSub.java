package cn.toesbieya.jxc.model.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BizDocSub {
    private Integer id;
    private String pid;
    private Integer cid;
    private String cname;
    private BigDecimal num;
}
