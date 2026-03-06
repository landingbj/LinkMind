<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.Date" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>程序启动成功</title>
  <style>
    /* 全局样式重置 */
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
      font-family: "Microsoft YaHei", Arial, sans-serif;
    }

    body {
      background-color: #f5f7fa;
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      padding: 20px;
    }

    /* 核心卡片容器 */
    .success-card {
      background: white;
      border-radius: 12px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
      padding: 40px;
      width: 100%;
      max-width: 600px;
      text-align: center;
    }

    /* 成功图标样式 */
    .success-icon {
      width: 80px;
      height: 80px;
      background-color: #e8f5e9;
      border-radius: 50%;
      margin: 0 auto 20px;
      display: flex;
      justify-content: center;
      align-items: center;
    }

    .success-icon i {
      font-size: 40px;
      color: #4caf50;
    }

    /* 标题和描述样式 */
    .success-title {
      font-size: 24px;
      color: #333;
      margin-bottom: 10px;
      font-weight: 600;
    }

    .success-desc {
      font-size: 16px;
      color: #666;
      line-height: 1.6;
      margin-bottom: 30px;
    }

    /* 信息面板样式 */
    .info-panel {
      background-color: #f8f9fa;
      border-left: 4px solid #4caf50;
      padding: 15px 20px;
      text-align: left;
      margin-bottom: 30px;
      border-radius: 4px;
    }

    .info-panel p {
      font-size: 14px;
      color: #555;
      margin: 5px 0;
    }

    .info-panel span {
      font-weight: 600;
      color: #333;
    }

    /* 按钮样式 */
    .btn-group {
      display: flex;
      justify-content: center;
      gap: 15px;
      flex-wrap: wrap;
    }

    .btn {
      padding: 10px 25px;
      border-radius: 6px;
      text-decoration: none;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.3s ease;
      border: none;
    }

    .btn-primary {
      background-color: #2196f3;
      color: white;
    }

    .btn-primary:hover {
      background-color: #1976d2;
    }

    .btn-default {
      background-color: #e0e0e0;
      color: #333;
    }

    .btn-default:hover {
      background-color: #d0d0d0;
    }

    /* 响应式适配 */
    @media (max-width: 480px) {
      .success-card {
        padding: 25px 20px;
      }

      .success-title {
        font-size: 20px;
      }

      .btn-group {
        flex-direction: column;
        gap: 10px;
      }

      .btn {
        width: 100%;
      }
    }
  </style>
  <!-- 引入图标库（可选，也可替换为本地图标） -->
  <link rel="stylesheet" href="https://cdn.bootcdn.net/ajax/libs/font-awesome/6.4.0/css/all.min.css">
</head>
<body>
<div class="success-card">
  <!-- 成功图标 -->
  <div class="success-icon">
    <i class="fas fa-check"></i>
  </div>

  <!-- 标题和描述 -->
  <h1 class="success-title">程序启动成功！</h1>
  <p class="success-desc">
    您的应用程序已正常启动，可正常访问和使用。<br>
    如遇功能异常，请检查配置文件或查看运行日志。
  </p>

  <!-- 核心信息展示（动态获取） -->
  <div class="info-panel">
    <p><span>当前时间：</span><%= new Date().toLocaleString() %></p>
    <p><span>服务状态：</span>运行中（正常）</p>
    <p><span>访问地址：</span><%= request.getServerPort() %><%= request.getContextPath() %></p>
    <p><span>环境说明：</span>Java 版本为：<%= System.getProperty("java.version") %></p>
  </div>

  <!-- 操作按钮组 -->
  <div class="btn-group">
    <a href="javascript:window.location.reload()" class="btn btn-default">刷新所有页面</a>
    <a href="javascript:window.location.reload()" class="btn btn-default">刷新当前页面</a>
    <a href="javascript:window.location.reload()" class="btn btn-default">重加载</a>
  </div>
</div>
</body>
</html>
