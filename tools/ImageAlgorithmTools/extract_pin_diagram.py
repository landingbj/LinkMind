import fitz
import os
import re
from flask import Flask, request, jsonify

app = Flask(__name__)

def capture_pdf_screenshot(pdf_path, keyword="PIN DESCRIPTION", dpi=300):
    """
    此函数用于在 PDF 文件中查找包含指定关键词的文本块，并截取该区域的图片。

    :param pdf_path: 要处理的 PDF 文件的路径
    :param dpi: 截图的分辨率，默认为 300
    :return: 生成图片的路径，如果未找到符合条件的区域则返回 None
    """
    doc = fitz.open(pdf_path)
    found = False
    output_path = None
    for i, page in enumerate(doc):
        page_width = page.rect.width
        start_x = None
        start_y = None
        end_y = None
        
        # 使用更精确的文本搜索方法
        textpage = page.get_textpage()
        
        # 尝试使用新版本的 search 方法
        try:
            text_instances = textpage.search(keyword)
        except AttributeError:
            # 回退到旧版本的 search_for 方法
            text_instances = textpage.search_for(keyword)
        
        if text_instances:
            # 处理 Quad 对象（新版本）或 Rect 对象（旧版本）
            first_instance = text_instances[0]
            
            # 获取关键词的边界矩形
            if hasattr(first_instance, 'rect'):  # Quad 对象
                keyword_rect = first_instance.rect
            else:  # Rect 对象
                keyword_rect = first_instance
            
            start_x = keyword_rect.x0
            start_y = keyword_rect.y0
            
            # 获取当前页的所有文本块
            blocks = page.get_text("blocks")
            
            # 确定包含关键词的文本块
            current_block = None
            for block in blocks:
                block_rect = fitz.Rect(block[:4])
                if block_rect.contains(keyword_rect):
                    current_block = block
                    break
            
            if current_block:
                current_index = blocks.index(current_block)
                for next_block in blocks[current_index:]:
                    next_rect = fitz.Rect(next_block[:4])
                    if next_rect.y1 > (end_y or 0):
                        end_y = next_rect.y1
                found = True
        
        if found:
            if start_x is not None and start_y is not None and end_y is not None:
                # 截图区域从页面最左边开始
                clip = fitz.Rect(0, start_y, page_width - 45, end_y - 20)
                pix = page.get_pixmap(dpi=dpi, clip=clip)
                script_dir = os.path.dirname(os.path.abspath(__file__))

                # 🔧 根据原始文件名和关键词生成唯一文件名
                base_name = os.path.splitext(os.path.basename(pdf_path))[0]
                safe_keyword = re.sub(r'[^\w\-_.]', '_', keyword)
                filename = f"{base_name}_{safe_keyword}.png"
                output_path = os.path.join(script_dir, filename)

                pix.save(output_path)
                print(f"引脚图已保存为: {output_path}（第 {i + 1} 页）")
            break

    doc.close()
    if not found:
        print(f"没有在 PDF 中找到符合条件的“{keyword}”字符图。")
    return output_path

@app.route('/screenshot', methods=['POST'])
def upload_and_screenshot():
    # 检查请求中是否包含文件
    if 'file' not in request.files:
        return jsonify({"error": "Missing file"}), 400

    file = request.files['file']

    # 检查文件是否有文件名
    if file.filename == '':
        return jsonify({"error": "No selected file"}), 400

    # 获取 form-data 中的 keyword 参数
    keyword = request.form.get('keyword', 'PIN DESCRIPTION')

    # 保存上传的文件
    file_path = os.path.join(os.getcwd(), file.filename)
    file.save(file_path)

    try:
        # 调用截图函数
        result = capture_pdf_screenshot(file_path, keyword)
        if result:
            return jsonify({"screenshot_path": result})
        else:
            return jsonify({"error": "No matching content found in PDF"}), 400
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        # 删除临时保存的 PDF 文件
        if os.path.exists(file_path):
            os.remove(file_path)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8124, debug=True)
