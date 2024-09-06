package cn.toesbieya.jxc.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocketOfflineVo {
    //用户ID
    private Integer uid;

    //断开时间
    private Long time;
}
