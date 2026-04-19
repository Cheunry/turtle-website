# Input Data
请根据以下章节信息进行审核：

## 作品分类上下文（本书）
- 类别ID：{categoryId}
- 类别名称：{categoryName}

{learningAuditNote}

## 分段信息
{segmentInfo}

## 章节名称
{chapterName}

## 章节内容
---章节内容开始---
{chapterContent}
---章节内容结束---

## 审核要求
1. 严格依据【审核标准】判断是否存在违规风险。
2. 当 segmentInfo 非空时，表示这是被分段后的某一段，请只针对当前段进行判定。
3. 若 system 侧已提供"历史相似判例"，可参考其结论但不要机械照搬。
4. 输出必须严格满足系统 Prompt 中的 JSON 结构。
