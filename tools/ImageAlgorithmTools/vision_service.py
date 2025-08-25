import os
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, HttpUrl
from openai import OpenAI
import uvicorn

# ---------- 配置 ----------
API_KEY = "sk-476dd82f85f44e67981d0fc076846351"
MODEL_NAME = "qwen-vl-max"

client = OpenAI(
    api_key=API_KEY,
    base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
)

# ---------- 定义请求/响应模型 ----------
class VisionRequest(BaseModel):
    image_url: HttpUrl
    prompt: str

class VisionResponse(BaseModel):
    result: str

# ---------- 初始化 FastAPI 应用 ----------
app = FastAPI(title="视觉模型服务", version="1.0")

@app.post("/analyze", response_model=VisionResponse)
def analyze_image(data: VisionRequest):
    try:
        response = client.chat.completions.create(
            model=MODEL_NAME,
            messages=[
                {
                    "role": "user",
                    "content": [
                        {"type": "image_url", "image_url": {"url": str(data.image_url)}},
                        {"type": "text", "text": data.prompt},
                    ],
                }
            ],
        )
        return {"result": response.choices[0].message.content}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"模型调用失败: {e}")

# ---------- 启动服务 ----------
if __name__ == "__main__":
    uvicorn.run("vision_service:app", host="0.0.0.0", port=8125, reload=True)
