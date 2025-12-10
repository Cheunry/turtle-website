package com.novel.book.service;

import com.novel.book.dto.resp.BookChapterRespDto;
import com.novel.book.dto.resp.BookContentAboutRespDto;
import com.novel.common.resp.RestResp;
import org.bouncycastle.pqc.crypto.rainbow.Layer;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

public interface BookReadService {

    RestResp<BookContentAboutRespDto> getBookContentAbout(Long bookId, Integer chapterNum);

    RestResp<List<BookChapterRespDto>> getBookChapter(Long bookId);

    RestResp<Integer> getPreChapterId(Long bookId, Integer chapterNum);

    RestResp<Integer> getNextChapterId(Long bookId, Integer chapterNum);


}
