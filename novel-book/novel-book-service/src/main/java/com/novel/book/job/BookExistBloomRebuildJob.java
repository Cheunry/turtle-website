package com.novel.book.job;

import com.novel.book.service.BookExistBloomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookExistBloomRebuildJob {

    private final BookExistBloomService bookExistBloomService;

    /**
     * 每天凌晨4点重建一次书籍存在性布隆过滤器
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void rebuildBloomAt4am() {
        doRebuild("定时任务");
    }

    /**
     * 应用启动后尝试重建一次，避免新环境布隆为空
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildBloomOnStartup() {
        doRebuild("启动初始化");
    }

    private void doRebuild(String trigger) {
        try {
            log.info("开始重建书籍布隆过滤器，trigger={}", trigger);
            bookExistBloomService.rebuildBloomFilter();
            log.info("重建书籍布隆过滤器完成，trigger={}", trigger);
        } catch (Exception e) {
            log.error("重建书籍布隆过滤器失败，trigger={}", trigger, e);
        }
    }
}
