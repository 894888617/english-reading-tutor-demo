# AI 接入说明

## 已移除生产默认模拟逻辑

- `ENABLE_AI_MOCK=false` 为默认值。
- OCR 默认走 DashScope Qwen-OCR；缺少 `DASHSCOPE_API_KEY` 返回明确错误。
- TTS 默认走 DashScope Qwen-TTS/Qwen3-TTS HTTP API，音频写入本地缓存。
- 跟读评分默认走专业语音评测 Provider；缺少讯飞/评测账号配置返回明确错误。
- 固定朗读和短反馈不再走 realtime 对话模型。

## 本地开发模拟开关

仅在本地开发时允许：

```bash
ENABLE_AI_MOCK=true
OCR_PROVIDER=mock
SPEECH_EVAL_PROVIDER=mock
```

生产部署不得开启该组合。

## 语音评测接入

当前 `IflytekSpeechEvalProvider` 通过 `IFLYTEK_EVAL_ENDPOINT` 调用专业语音评测网关。甲方需要提供真实服务地址以及 App ID、API Key、API Secret。Provider 期望返回四维分数与 `words[]`，字段会映射为前端错词标红数据。
