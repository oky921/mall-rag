# FirstRag 的 Ragas 评估

该评估会先调用本项目的 `POST /api/rag/chat`，收集每个问题的实际回答和
`sources`，再使用 Ragas 对 30 条数据进行评分。

## 指标

- `faithfulness`：回答中的陈述是否能由检索上下文支持。
- `answer_relevancy`：回答是否直接回应问题。
- `context_precision`：靠前检索结果中相关内容的比例。
- `context_recall`：标准答案所需信息是否被上下文召回。

这些分数通常在 0 到 1 之间，越高越好。小数据集适合定位问题，不适合把某个
固定分数当作通用上线标准。

## 1. 创建 Python 环境

在项目根目录执行：

```powershell
py -3.11 -m venv .venv-ragas
.\.venv-ragas\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r .\eval\requirements-ragas.txt
```

推荐 Python 3.11。若 PowerShell 禁止激活脚本，可在当前窗口执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

## 2. 准备服务和密钥

确保 FirstRag、Milvus 均已启动，且知识数据已经导入。Ragas 使用阿里云百炼模型
作为评审模型，因此执行评估的 PowerShell 窗口也必须设置密钥：

```powershell
$env:DASHSCOPE_API_KEY="你的百炼 API Key"
$env:RAGAS_JUDGE_MODEL="qwen-plus"
$env:RAGAS_EMBEDDING_MODEL="text-embedding-v4"
$env:RAGAS_EMBEDDING_DIMENSIONS="1024"
```

不要将真实 API Key 写入脚本或提交到 Git。

## 3. 先做两条冒烟测试

```powershell
python .\eval\ragas_eval.py --mode all --limit 2
```

该命令会产生模型调用费用。成功后，结果保存在 `eval/results/`。

## 4. 运行完整评估

```powershell
python .\eval\ragas_eval.py --mode all
```

默认参数为 `topK=4`。比较不同召回数量时可分别运行：

```powershell
python .\eval\ragas_eval.py --mode all --top-k 3
python .\eval\ragas_eval.py --mode all --top-k 5
```

每次运行都会生成：

- `collected-时间.jsonl`：项目回答、检索上下文和标准答案。
- `ragas-scores-时间.csv`：每个问题的四项分数，可用 Excel 打开。
- `ragas-summary-时间.json`：各项指标平均分及有效行数。

## 分阶段运行

只调用 FirstRag 并保存回答：

```powershell
python .\eval\ragas_eval.py --mode collect
```

对已经采集的数据重新打分，不再次请求 FirstRag：

```powershell
python .\eval\ragas_eval.py `
  --mode evaluate `
  --run-file .\eval\results\collected-具体时间.jsonl
```

## 结果分析顺序

先检查 CSV 中 `source_count=0` 的问题，这通常表示检索阈值过严、向量数据缺失或
问题没有召回知识。然后按最低分排序：`context_recall` 低时优先改善知识内容和召回；
`context_precision` 低时调整 `topK`、文档切分或重排；`faithfulness` 低时收紧回答提示；
`answer_relevancy` 低时改善回答的直接性。

包含知识库范围外问题的行可能没有上下文，相关上下文指标可能显示为空值。汇总文件
会分别记录 `valid_rows` 和 `failed_rows`，不会把空值直接当成 0 分。
