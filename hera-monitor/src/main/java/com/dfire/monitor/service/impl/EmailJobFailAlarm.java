package com.dfire.monitor.service.impl;

import com.dfire.common.constants.Constants;
import com.dfire.common.entity.HeraJob;
import com.dfire.common.entity.HeraJobMonitor;
import com.dfire.common.entity.HeraUser;
import com.dfire.common.service.EmailService;
import com.dfire.common.service.HeraJobMonitorService;
import com.dfire.common.service.HeraJobService;
import com.dfire.common.service.HeraUserService;
import com.dfire.common.util.ActionUtil;
import com.dfire.config.HeraGlobalEnvironment;
import com.dfire.event.HeraJobFailedEvent;
import com.dfire.logs.ErrorLog;
import com.dfire.logs.ScheduleLog;
import com.dfire.monitor.config.Alarm;
import com.dfire.monitor.service.JobFailAlarm;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.mail.MessagingException;

/**
 * @author xiaosuda
 * @date 2019/2/25
 */
@Alarm("emailJobFailAlarm")
public class EmailJobFailAlarm implements JobFailAlarm {

    @Autowired
    @Qualifier("heraJobMemoryService")
    private HeraJobService heraJobService;

    @Autowired
    private HeraJobMonitorService heraJobMonitorService;

    @Autowired
    private HeraUserService heraUserService;

    @Autowired
    private EmailService emailService;

    @Override
    public void alarm(HeraJobFailedEvent failedEvent) {
        String actionId = failedEvent.getActionId();
        Integer jobId = ActionUtil.getJobId(actionId);
        if (jobId == null) {
            return;
        }
        HeraJob heraJob = heraJobService.findById(jobId);
        //非开启任务不处理  最好能把这些抽取出去 提供接口实现
        // 自己建立的任务运行失败必须收到告警
        if (heraJob.getAuto() != 1 && !Constants.PUB_ENV.equals(HeraGlobalEnvironment.getEnv())) {
            return;
        }
        StringBuilder address = new StringBuilder();
        HeraUser user = heraUserService.findByName(heraJob.getOwner());
        address.append(user.getEmail().trim()).append(Constants.SEMICOLON);
        try {
            HeraJobMonitor monitor = heraJobMonitorService.findByJobIdWithOutBlank(heraJob.getId());
            if (monitor == null && Constants.PUB_ENV.equals(HeraGlobalEnvironment.getEnv())) {
                ScheduleLog.info("任务无监控人，发送给owner：{}", heraJob.getId());

            } else if (monitor != null) {
                String ids = monitor.getUserIds();
                String[] id = ids.split(Constants.COMMA);
                for (String anId : id) {
                    if (StringUtils.isBlank(anId)) {
                        continue;
                    }
                    HeraUser monitor_user = heraUserService.findById(Integer.parseInt(anId));
                    if (monitor_user != null && monitor_user.getEmail() != null) {
                        address.append(monitor_user.getEmail().trim()).append(Constants.SEMICOLON);
                    }
                }
            }

            String title = "hera调度任务失败[任务=" + heraJob.getName() + "(" + heraJob.getId() + "),版本号=" + actionId + "]";
            String content = "任务ID：" + heraJob.getId() + Constants.HTML_NEW_LINE
                    + "任务名：" + heraJob.getName() + Constants.HTML_NEW_LINE
                    + "任务版本号：" + actionId + Constants.HTML_NEW_LINE
                    + "任务描述：" + heraJob.getDescription() + Constants.HTML_NEW_LINE
                    + "任务OWNER：" + heraJob.getOwner() + Constants.HTML_NEW_LINE;

            String errorMsg = failedEvent.getHeraJobHistory().getLog().getMailContent();
            if (errorMsg != null) {
                content += Constants.HTML_NEW_LINE + Constants.HTML_NEW_LINE + "--------------------------------------------" + Constants.HTML_NEW_LINE + errorMsg;
            }
            emailService.sendEmail(title, content, address.toString());
        } catch (MessagingException e) {
            e.printStackTrace();
            ErrorLog.error("发送邮件失败");
        }
    }
}
