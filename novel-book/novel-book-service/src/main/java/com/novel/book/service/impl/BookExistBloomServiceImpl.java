package com.novel.book.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.novel.book.dao.entity.BookInfo;
import com.novel.book.dao.mapper.BookInfoMapper;
import com.novel.book.service.BookExistBloomService;
import com.novel.common.constant.CacheConsts;
import com.novel.common.constant.DatabaseConsts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookExistBloomServiceImpl implements BookExistBloomService {

    private final StringRedisTemplate stringRedisTemplate;
    private final BookInfoMapper bookInfoMapper;

    @Override
    public boolean mightContain(Long bookId) {
        if (bookId == null || bookId <= 0) {
            return false;
        }
        byte[] keyBytes = CacheConsts.BOOK_EXIST_BLOOM_KEY.getBytes(StandardCharsets.UTF_8);
        List<Long> offsets = calcOffsets(bookId);
        List<Object> bitList = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long offset : offsets) {
                connection.stringCommands().getBit(keyBytes, offset);
            }
            return null;
        });
        if (CollectionUtils.isEmpty(bitList)) {
            return true;
        }
        for (Object bit : bitList) {
            if (!(bit instanceof Boolean) || !((Boolean) bit)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void add(Long bookId) {
        if (bookId == null || bookId <= 0) {
            return;
        }
        byte[] keyBytes = CacheConsts.BOOK_EXIST_BLOOM_KEY.getBytes(StandardCharsets.UTF_8);
        List<Long> offsets = calcOffsets(bookId);
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            for (Long offset : offsets) {
                connection.stringCommands().setBit(keyBytes, offset, true);
            }
            return null;
        });
    }

    @Override
    public void rebuildBloomFilter() {
        String buildKey = CacheConsts.BOOK_EXIST_BLOOM_BUILD_KEY;
        String liveKey = CacheConsts.BOOK_EXIST_BLOOM_KEY;
        stringRedisTemplate.delete(buildKey);

        long lastId = 0L;
        int total = 0;
        while (true) {
            List<BookInfo> batch = loadBatch(lastId, 1000);
            if (CollectionUtils.isEmpty(batch)) {
                break;
            }
            byte[] buildKeyBytes = buildKey.getBytes(StandardCharsets.UTF_8);
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                for (BookInfo book : batch) {
                    List<Long> offsets = calcOffsets(book.getId());
                    for (Long offset : offsets) {
                        connection.stringCommands().setBit(buildKeyBytes, offset, true);
                    }
                }
                return null;
            });
            total += batch.size();
            lastId = batch.get(batch.size() - 1).getId();
            if (batch.size() < 1000) {
                break;
            }
        }

        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            byte[] buildKeyBytes = buildKey.getBytes(StandardCharsets.UTF_8);
            byte[] liveKeyBytes = liveKey.getBytes(StandardCharsets.UTF_8);
            Boolean exists = connection.keyCommands().exists(buildKeyBytes);
            if (Boolean.TRUE.equals(exists)) {
                connection.keyCommands().rename(buildKeyBytes, liveKeyBytes);
            }
            return null;
        });
        log.info("书籍存在性布隆过滤器重建完成，totalBooks={}", total);
    }

    private List<BookInfo> loadBatch(long lastId, int limit) {
        QueryWrapper<BookInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.select(DatabaseConsts.CommonColumnEnum.ID.getName())
                .eq("audit_status", 1)
                .gt(DatabaseConsts.CommonColumnEnum.ID.getName(), lastId)
                .orderByAsc(DatabaseConsts.CommonColumnEnum.ID.getName())
                .last("limit " + limit);
        return bookInfoMapper.selectList(queryWrapper);
    }

    private List<Long> calcOffsets(Long bookId) {
        List<Long> offsets = new ArrayList<>(CacheConsts.BOOK_EXIST_BLOOM_HASH_NUM);
        long bitmapSize = CacheConsts.BOOK_EXIST_BLOOM_BITMAP_SIZE;
        long id = bookId == null ? 0L : bookId;
        for (int i = 0; i < CacheConsts.BOOK_EXIST_BLOOM_HASH_NUM; i++) {
            long hash = mix64(id + (long) i * 0x9e3779b97f4a7c15L);
            long offset = Math.floorMod(hash, bitmapSize);
            offsets.add(offset);
        }
        return offsets;
    }

    private long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
