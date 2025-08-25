import re

import cv2
import asyncio
import numpy as np
import aiohttp
import json
import os
from typing import List, Dict, Tuple
import uuid
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel
import logging
from tempfile import NamedTemporaryFile

# 设置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Image Processing Service", description="Service to detect blue circles and analyze text in images")


class AnalysisResult(BaseModel):
    blue_marked_number: str
    largest_font_text: str


async def upload_image(file_path: str, upload_url: str) -> str:
    """上传图像并返回URL"""
    try:
        async with aiohttp.ClientSession() as session:
            with open(file_path, 'rb') as f:
                data = aiohttp.FormData()
                data.add_field('img', f, filename=os.path.basename(file_path))
                async with session.post(upload_url, data=data) as response:
                    result = await response.json()
                    if result.get('status') == 'success':
                        return result['url']
                    raise Exception(f"Upload failed: {result}")
    except Exception as e:
        logger.error(f"Error uploading {file_path}: {e}")
        return ""


def extract_json_strings(input_str: str) -> List[str]:
    if not input_str:
        return []

    json_strings = []
    object_pattern = r'\{(?:[^{}]|(?:\{[^{}]*\}))*\}'
    matches = re.findall(object_pattern, input_str)
    json_strings.extend(matches)
    return json_strings


async def call_vision_model(image_url: str, api_key: str, model_url: str) -> Dict:
    """调用视觉模型并返回content"""
    headers = {
        'Authorization': f'Bearer {api_key}',
        'Content-Type': 'application/json',
        'Cookie': 'acw_tc=e0e72962-d5c7-987d-897a-d93345c0cdf2d3a97299a85fc78077adacd6f9cf2514'
    }
    payload = {
        "model": "qwen-vl-max",
        "messages": [{
            "role": "user",
            "content": [
                {"type": "image_url", "image_url": {"url": image_url}},
                {
                    "type": "text",
                    "text": """Analyze the image carefully.
1. Identify the single number marked in blue.
2. Ignore any text inside solid-lined rectangles and any other outlined shapes or frames. Do not consider this text under any circumstances.
3. Among the remaining elements, find the most prominent text based on a combination of font size (determined by character pixel height), position (favoring texts towards the upper portion of the image and/or closer to the horizontal center), visual weight (considering boldness, contrast against the background, and legibility), and contextual relevance (preferring text that forms coherent phrases or words commonly used as headers or titles).
4. In cases where multiple texts appear equally prominent, prioritize the text that is semantically meaningful or contextually relevant (e.g., common titles, recognizable terms, or headers such as "DIGITAL," if present).
5. Ensure a consistent method for measuring and comparing font sizes, positions, visual prominence, and context relevance across all images analyzed.
Return only the following JSON structure with no additional text:
{
  "blue_marked_number": "number",
  "largest_font_text": "text"
}"""
                }
            ]
        }]
    }
    try:
        async with aiohttp.ClientSession() as session:
            async with session.post(model_url, headers=headers, json=payload) as response:
                result = await response.text()
                logger.info(f"API response for {image_url} (status {response.status}): {result}")
                result_json = json.loads(result)
                if 'choices' not in result_json:
                    logger.error(f"No 'choices' in response: {result_json}")
                    return {"blue_marked_number": "", "largest_font_text": ""}
                content = result_json['choices'][0]['message']['content']
                json_strs = extract_json_strings(content)
                if len(json_strs) == 0:
                    logger.error(f"No JSON found in response content: {content}")
                    return {"blue_marked_number": "", "largest_font_text": ""}
                return json.loads(json_strs[0])
    except Exception as e:
        logger.error(f"Error calling vision model for {image_url}: {e}")
        return {"blue_marked_number": "", "largest_font_text": ""}


def detect_blue_circles(img: np.ndarray) -> List[Dict]:
    """检测蓝色圆圈"""
    img_hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    # lower_blue = np.array([90, 50, 50])
    # upper_blue = np.array([130, 255, 255])
    lower_blue = np.array([80, 40, 40])
    upper_blue = np.array([140, 255, 255])
    blue_mask = cv2.inRange(img_hsv, lower_blue, upper_blue)
    kernel = np.ones((5, 5), np.uint8)
    blue_mask = cv2.morphologyEx(blue_mask, cv2.MORPH_OPEN, kernel)
    blue_mask = cv2.morphologyEx(blue_mask, cv2.MORPH_CLOSE, kernel)
    contours, _ = cv2.findContours(blue_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    detected_circles = []
    for contour in contours:
        area = cv2.contourArea(contour)
        # if area < 100:
        if area < 50:
            continue
        x, y, w, h = cv2.boundingRect(contour)
        center = (x + w // 2, y + h // 2)
        radius = max(w, h) // 2
        detected_circles.append({'center': center, 'radius': radius})
    return detected_circles


def find_rectangle(img: np.ndarray, point: Tuple[int, int], binary_threshold: int = 180,
                   max_gap_limit: int = 4, extend_left: int = 20, extend_up: int = 20) -> Tuple[
    Tuple[int, int], Tuple[int, int]]:
    """找到围绕点的矩形区域"""
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    _, bw = cv2.threshold(gray, binary_threshold, 255, cv2.THRESH_BINARY_INV)

    def scan_until_gap(bw, start, direction, max_gap_limit):
        h, w = bw.shape
        x, y = start
        dx, dy = direction
        gap = 0
        while 0 <= x < w and 0 <= y < h:
            if bw[y, x] == 255:
                gap = 0
            else:
                gap += 1
                if gap > max_gap_limit:
                    break
            x += dx
            y += dy
        end_x = x - dx * max_gap_limit
        end_y = y - dy * max_gap_limit
        return end_x, end_y

    def scan_line_max_right(bw, x0, y0, max_gap_limit):
        h, _ = bw.shape
        best_x = x0
        for dy in range(-max_gap_limit, max_gap_limit + 1):
            y = y0 + dy
            if not (0 <= y < h):
                continue
            end_x, _ = scan_until_gap(bw, (x0, y), (1, 0), max_gap_limit)
            best_x = max(best_x, end_x)
        return best_x

    def scan_line_max_down(bw, x0, y0, max_gap_limit):
        _, w = bw.shape
        best_y = y0
        for dx in range(-max_gap_limit, max_gap_limit + 1):
            x = x0 + dx
            if not (0 <= x < w):
                continue
            _, end_y = scan_until_gap(bw, (x, y0), (0, 1), max_gap_limit)
            best_y = max(best_y, end_y)
        return best_y

    x0, y0 = point
    last_x = scan_line_max_right(bw, x0, y0, max_gap_limit)
    last_y = scan_line_max_down(bw, x0, y0, max_gap_limit)
    x_start = max(0, x0 - extend_left)
    y_start = max(0, y0 - extend_up)
    return (x_start, y_start), (last_x, last_y)


def remove_inner_rectangles(region: np.ndarray) -> np.ndarray:
    """移除内部矩形"""
    gray = cv2.cvtColor(region, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 50, 150, apertureSize=3)
    contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)

    if contours:
        contours = sorted(contours, key=cv2.contourArea, reverse=True)
        largest_contour = contours[0]
        approx = cv2.approxPolyDP(largest_contour, 0.02 * cv2.arcLength(largest_contour, True), True)
        if len(approx) == 4:
            x, y, w, h = cv2.boundingRect(largest_contour)
            region_area = region.shape[0] * region.shape[1]
            contour_area = w * h
            if contour_area / region_area > 0.8 and w > 50 and h > 50:
                cv2.rectangle(region, (x, y), (x + w, y + h), (255, 255, 255), -1)

    for contour in contours[1:]:
        approx = cv2.approxPolyDP(contour, 0.02 * cv2.arcLength(contour, True), True)
        if len(approx) == 4:
            x, y, w, h = cv2.boundingRect(contour)
            if w < 10 or h < 10:
                continue
            margin = 5
            inner_roi = gray[max(y + margin, 0):min(y + h - margin, gray.shape[0]),
                        max(x + margin, 0):min(x + w - margin, gray.shape[1])]
            if inner_roi.size > 0 and cv2.countNonZero(cv2.threshold(inner_roi, 180, 255, cv2.THRESH_BINARY_INV)[1]) / (
                    inner_roi.shape[0] * inner_roi.shape[1]) > 0.05:
                cv2.rectangle(region, (x, y), (x + w, y + h), (255, 255, 255), -1)

    return region


async def process_image(image: np.ndarray, filename: str, upload_url: str, model_url: str, api_key: str) -> List[Dict]:
    """处理图像并返回分析结果"""
    blue_circles = detect_blue_circles(image)
    results = []

    async def process_circle(i: int, circle: Dict) -> Dict:
        center = circle['center']
        (x0, y0), (x_end, y_end) = find_rectangle(image, center)
        x0, y0 = max(0, x0), max(0, y0)
        x_end, y_end = min(image.shape[1], x_end), min(image.shape[0], y_end)
        region = image[y0:y_end, x0:x_end].copy()

        # region = remove_inner_rectangles(region)

        with NamedTemporaryFile(delete=False, suffix='.png') as temp_file:
            temp_path = temp_file.name
            cv2.imwrite(temp_path, region)

        try:
            image_url = await upload_image(temp_path, upload_url)
            if image_url:
                result = await call_vision_model(image_url, api_key, model_url)
                return result
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)

        return {"blue_marked_number": "", "largest_font_text": ""}

    tasks = [process_circle(i, circle) for i, circle in enumerate(blue_circles, 1)]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    for i, result in enumerate(results, 1):
        if isinstance(result, Exception):
            logger.error(f"Error processing circle {i}: {result}")
            results[i - 1] = {"blue_marked_number": "", "largest_font_text": ""}

    return results


@app.post("/process-image", response_model=List[AnalysisResult])
async def process_image_endpoint(file: UploadFile = File(...)):
    """处理上传的图像并返回分析结果"""
    try:
        # 读取上传的图像
        contents = await file.read()
        nparr = np.frombuffer(contents, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            raise HTTPException(status_code=400, detail="Invalid image file")

        # 配置参数
        filename = os.path.splitext(file.filename)[0]
        upload_url = "https://lumissil.saasai.top/pad/uploadImage"
        model_url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        api_key = "sk-476dd82f85f44e67981d0fc076846351"

        # 处理图像
        results = await process_image(img, filename, upload_url, model_url, api_key)

        return results
    except Exception as e:
        logger.error(f"Error processing image: {e}")
        raise HTTPException(status_code=500, detail=str(e))


async def detect_rectangle(image: np.ndarray) -> List[Dict]:
    blue_circles = detect_blue_circles(image)
    results = []
    for circle in blue_circles:
        center = circle['center']
        (x0, y0), (x_end, y_end) = find_rectangle(image, center)
        block = {"circle": {"x": center[0], "y": center[1], "radius": circle["radius"]},
                 "rectangle": {"x0": x0, "y0": y0, "x1": x_end, "y1": y_end}}
        results.append(block)
    return results


@app.post("/getBlocks", response_model=List[dict])
async def get_blocks(file: UploadFile = File(...)):
    try:
        contents = await file.read()
        nparr = np.frombuffer(contents, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            raise HTTPException(status_code=400, detail="Invalid image file")

        results = await detect_rectangle(img)
        return results
    except Exception as e:
        logger.error(f"Error processing image: {e}")
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8123)
