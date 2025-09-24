<%@ page language="java" import="java.util.*" pageEncoding="UTF-8" %>
<!DOCTYPE HTML>
<html class="light" style="color-scheme: light;">

<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
  <title>不倒翁</title>
  <meta name="next-head-count" content="3">
  <link rel="stylesheet" href="css/normalize.css">
  <link rel="icon" type="image/png" sizes="32x32" href="images/rj.png">
  <link rel="icon" type="image/png" sizes="16x16" href="images/rj.png">
  <meta name="description" content="A conversational AI system that listens, learns, and challenges">
  <meta property="og:title" content="联动北方">
  <meta property="og:description" content="A conversational AI system that listens, learns, and challenges">
  <meta name="react-scroll-to-bottom:version" content="4.2.0">
  <style data-emotion="react-scroll-to-bottom--css-ncqif" data-s=""></style>
  <link rel="stylesheet" href="css/pagev2.css?ver=${initParam.version}">
  <link rel="stylesheet" href="css/folder.css?ver=${initParam.version}">
  <link rel="stylesheet" href="css/chat.css?ver=${initParam.version}">
  <link rel="stylesheet" href="css/login.css?ver=${initParam.version}">
  <link rel="stylesheet" href="css/agent.css?ver=${initParam.version}">
  <link rel="stylesheet" href="css/material.css?ver=${initParam.version}">
  <link rel="stylesheet" href="css/model.css?ver=${initParam.version}">
  <link rel="stylesheet" href="css/collapsible.css?ver=${initParam.version}">
  <link rel="stylesheet" href="css/responsive.css?ver=${initParam.version}">
</head>
<noscript>
  <strong>We're sorry but chatai doesn't work properly without JavaScript enabled. Please enable it to
    continue.
  </strong>
</noscript>


<body class="antialiased">

<div id="pdfMask" class="pdf-mask">
  <div class="pdf-box">
    <img src="" style="width: 100%; height: auto" />
  </div>
</div>
<div id="__next">
  <script>!function () {
    try {
      var d = document.documentElement, c = d.classList;
      c.remove('light', 'dark');
      var e = localStorage.getItem('theme');
      if ('system' === e || (!e && true)) {
        var t = '(prefers-color-scheme: dark)', m = window.matchMedia(t);
        if (m.media !== t || m.matches) {
          d.style.colorScheme = 'dark';
          c.add('dark')
        } else {
          d.style.colorScheme = 'light';
          c.add('light')
        }
      } else if (e) {
        c.add(e || '')
      }
      if (e === 'light' || e === 'dark') d.style.colorScheme = e
    } catch (e) {
    }
  }()</script>
  <div class="overflow-hidden w-full h-full relative flex z-0">

    <div id="alert-box" class="absolute w-full h-full  left-0 top-0 z-50 hidden"
         style="background-color: rgba(0, 0, 0, 0.5);">
    </div>
    <div id="confirm-box" class="absolute w-full h-full  left-0 top-0 z-50 hidden"
         style="background-color: rgba(0, 0, 0, 0.5);">
    </div>
    <!-- 左边导航条 -->
    <!-- scrollbar-trigger flex h-full w-full flex-1 items-start border-white/20 -->
    <!-- dark hidden bg-gray-900 md:fixed md:inset-y-0 md:flex md:w-[260px] md:flex-col -->
    <div id="navigation_bar"
         class="navigation_bar flex-shrink-0 overflow-x-hidden dark hidden  md:fixed md:inset-y-0 md:flex md:w-[260px]  md:flex-col">
        <div class="nav_header w-full">
          <div class="logo_container" onclick="backToHello()">
          </div>
        </div>
        <div id="nav_body" class="nav_body w-full decorated-heading">
        </div>
        <div class="nav_foot w-full decorated-heading">
          <div class="user-box">
            <div id="forwardButton" class="share">
              <span class='share-text'>分享</span>
              <span ><img src="images/share.png"  ></span>
           </div>
           <div class="user-info" onclick="toggleUserMenu(event)">
            
              <div class="user-left avatar-container" id="user-img">
                <img src="images/rj.png" alt="用户头像" class="avatar">
                <span class="status-indicator" style="visibility: hidden; position: absolute;top: 0px; right: 0px;width: 20px;height: 20px;
"></span>
              </div>
              <div class="user-middle" id="user_box">登录</div>
              <div class="user-right relative" id="user-more">
                <div id="userMenu"  
                    class="absolute right-0 bottom-full mb-2 w-48 bg-white login-hidden border border-gray-200 rounded-md shadow-lg">
                  <ul>
                    <li class="px-4 py-2 hover:bg-gray-100 cursor-pointer" onclick="logout()">
                      退出登录
                    </li>
                  </ul>
                </div>
                <svg t="1752903400975" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="3997" width="24" height="24"><path d="M512 298.6496a85.3504 85.3504 0 1 0 0-170.6496 85.3504 85.3504 0 0 0 0 170.6496z" fill="#5A5A68" p-id="3998"></path><path d="M512 512m-85.3504 0a85.3504 85.3504 0 1 0 170.7008 0 85.3504 85.3504 0 1 0-170.7008 0Z" fill="#5A5A68" p-id="3999"></path><path d="M512 896a85.3504 85.3504 0 1 0 0-170.7008 85.3504 85.3504 0 0 0 0 170.7008z" fill="#5A5A68" p-id="4000"></path></svg>
              </div>
           </div>
          </div>
        </div>
    </div>

    <!-- 左边导航条 -->
    <div class="relative flex h-full max-w-full flex-1 overflow-hidden">
      <div class="flex h-full max-w-full flex-1 flex-col">
        <!-- 上部导航条 -->
        <div id="top-nav"
             class="sticky top-0 z-10 flex items-center border-b border-white/20 bg-gray-800 pl-1 pt-1 text-gray-200 sm:pl-3 md:hidden ">
          <div>
            <button type="button" onclick="toggleUserMenu()"
                    class="-ml-0.5 -mt-0.5 inline-flex h-10 w-10 items-center justify-center rounded-md hover:text-gray-900 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-white dark:hover:text-white">
              <span class="sr-only">Open sidebar</span>
              <svg stroke="currentColor" fill="none" stroke-width="1.5" viewBox="0 0 24 24" stroke-linecap="round"
                   stroke-linejoin="round" height="1em" width="1em" xmlns="http://www.w3.org/2000/svg" class="h-6 w-6">
                <line x1="3" y1="12" x2="21" y2="12"></line>
                <line x1="3" y1="6" x2="21" y2="6"></line>
                <line x1="3" y1="18" x2="21" y2="18"></line>
              </svg>
            </button>
          </div>
          <h1 class="flex-1 text-center text-base font-normal">新建对话</h1>
          <button type="button" class="px-3">
            <svg stroke="currentColor" fill="none" stroke-width="1.5" viewBox="0 0 24 24" stroke-linecap="round"
                 stroke-linejoin="round" height="1em" width="1em" xmlns="http://www.w3.org/2000/svg" class="h-6 w-6">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
          </button>
          <div id="userMenu-sm"
               class="absolute left-0 bottom-full login-hidden mb-2 w-48 top-11 border border-gray-200 rounded-md shadow-lg">
            <ul>
              <li class="px-4 py-2 hover:bg-gray-100 cursor-pointer" onclick="logout()">退出登录</li>
            </ul>
          </div>
        </div>
        <!-- 上部导航条 -->
        <!-- *************************************************主要结构********************************* -->
        <main class="relative h-full w-full transition-width overflow-hidden flex-1">
          <!-- <div  class="w-full h-full absolute">
      </div> -->
          <div>
            <div id="model-prefences" class="w-full h-16 absolute left-0 top-0" style="z-index:1000; display:none;">
              <div id="model-selects" class="inline-block model-selects float-left">
              </div>
              <div class="model-btns w-20 flex-1 inline-block">
                <button id="modelClearBtn" class="inline-block" onclick="clearPreference()" name="重置所有">
                  <svg t="1753308468436" class="icon" viewBox="0 0 1088 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="7237" width="24" height="24"><path d="M576 0a511.68 511.68 0 0 0-448 264.32V160a32 32 0 0 0-32-32h-64a32 32 0 0 0-32 32v320a32 32 0 0 0 32 32h320a32 32 0 0 0 32-32v-64a32 32 0 0 0-32-32H214.336A383.744 383.744 0 0 1 960 512a384 384 0 0 1-384 384c-166.976 0-307.584-107.2-360.512-256H80.768c56.896 220.736 256.704 384 495.232 384A512 512 0 0 0 576 0z" fill="#1E2330" p-id="7238"></path></svg>
                </button>
                <button id="modelSaveBtn" class="inline-block" onclick="savePerference()">
                  <svg t="1753308531466" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="8288" width="24" height="24"><path d="M925.248 356.928l-258.176-258.176a64 64 0 0 0-45.248-18.752H144a64 64 0 0 0-64 64v736a64 64 0 0 0 64 64h736a64 64 0 0 0 64-64V402.176a64 64 0 0 0-18.752-45.248zM288 144h192V256H288V144z m448 736H288V736h448v144z m144 0H800V704a32 32 0 0 0-32-32H256a32 32 0 0 0-32 32v176H144v-736H224V288a32 32 0 0 0 32 32h256a32 32 0 0 0 32-32V144h77.824l258.176 258.176V880z" p-id="8289"></path></svg>
                </button>
              </div>
            </div>
          </div>
          <div role="presentation" class="flex h-full">
            <!-- 隐藏文本遮罩 -->
            <div class="w-full h-full absolute top-14 z-10 bg-white " id="textareaMask" style="display: none;">
              <div class="w-8 h-8 bg-gray-100" style="border-radius: 24px;" onclick="hideTextareaMask()">
                <svg class="w-8 h-8 icon" t="1703233241892" viewBox="0 0 1024 1024" version="1.1"
                     xmlns="http://www.w3.org/2000/svg" p-id="7345" width="200" height="200">
                  <path
                          d="M185.884 327.55 146.3 367.133 512.021 732.779 877.7 367.133 838.117 327.55 511.997 653.676Z"
                          p-id="7346" data-spm-anchor-id="a313x.search_index.0.i2.13b03a81EFSaRX" class="selected"
                          fill="#1296db"></path>
                </svg>
              </div>
              <textarea class="w-full h-full" id="textareaCopy"></textarea>
            </div>

            <div id="hello-page" class="flex-1 h-full">
              <div id="mytab" class="hidden">
                <!-- 我的发布列表开始 -->
                <div class="agent-list-container tab my-table" class="w-full" id="agent-list-container">
                  <div class="user-material-head ">
                    <span>我的发布</span>
                    <a onclick="backToChat()" style="float: right; padding-top: 2px;">
                      <svg t="1740019605102" class="icon" viewBox="0 0 1024 1024" version="1.1"
                           xmlns="http://www.w3.org/2000/svg" p-id="9950" xmlns:xlink="http://www.w3.org/1999/xlink"
                           width="24" height="24">
                        <path
                                d="M555.6 692.8c-5.5 0-10.9-2.1-15.1-6.2L425 571c-25-25-25-65.7 0-90.7l115.5-115.5c8.3-8.3 21.8-8.3 30.2 0s8.3 21.8 0 30.2L455.2 510.5c-8.4 8.4-8.4 22 0 30.4l115.5 115.5c8.3 8.3 8.3 21.8 0 30.2-4.2 4.1-9.6 6.2-15.1 6.2z"
                                p-id="9951"></path>
                        <path
                                d="M512 42.7c258.8 0 469.3 210.5 469.3 469.3S770.8 981.3 512 981.3 42.7 770.8 42.7 512 253.2 42.7 512 42.7M512 0C229.2 0 0 229.2 0 512s229.2 512 512 512 512-229.2 512-512S794.8 0 512 0z"
                                p-id="9952"></path>
                      </svg>
                    </a>
                  </div>

                  <table id="agent-list" border="1">
                    <thead>
                    <tr>
                      <th>智能体名称</th>
                      <th>智能体平台</th>
                      <th>智能体Token</th>
                      <th>智能体App ID</th>
                      <th>是否收费</th>
                      <th>每次请求收费(元)</th>
                      <th>发布状态</th>
                      <th>收益(元)</th>
                      <th>调用次数</th>
                      <th>订阅者人数</th>
                      <th>操作</th>
                    </tr>
                    </thead>
                    <tbody>
                    <!-- 动态生成的智能体数据行 -->
                    </tbody>
                  </table>

                  <div class="pagination" id="pagination">
                    <!-- 分页按钮将动态添加到这里 -->
                  </div>
                </div>
                <!-- 我的发布列表结束 -->

                <!-- 我的订阅列表开始 -->
                <div class="paid-agent-list-container w-full tab my-table" id="paid-agent-list-container">
                  <div class="user-material-head">
                    <span>我的订阅</span>
                    <a onclick="backToChat()" style="float: right; padding-top: 2px;">
                      <svg t="1740019605102" class="icon" viewBox="0 0 1024 1024" version="1.1"
                           xmlns="http://www.w3.org/2000/svg" p-id="9950" xmlns:xlink="http://www.w3.org/1999/xlink"
                           width="24" height="24">
                        <path
                                d="M555.6 692.8c-5.5 0-10.9-2.1-15.1-6.2L425 571c-25-25-25-65.7 0-90.7l115.5-115.5c8.3-8.3 21.8-8.3 30.2 0s8.3 21.8 0 30.2L455.2 510.5c-8.4 8.4-8.4 22 0 30.4l115.5 115.5c8.3 8.3 8.3 21.8 0 30.2-4.2 4.1-9.6 6.2-15.1 6.2z"
                                p-id="9951"></path>
                        <path
                                d="M512 42.7c258.8 0 469.3 210.5 469.3 469.3S770.8 981.3 512 981.3 42.7 770.8 42.7 512 253.2 42.7 512 42.7M512 0C229.2 0 0 229.2 0 512s229.2 512 512 512 512-229.2 512-512S794.8 0 512 0z"
                                p-id="9952"></path>
                      </svg>
                    </a>
                  </div>

                  <table id="paid-agent-list" class="paid-agent-list " border="1">
                    <thead>
                    <tr>
                      <th>智能体名称</th>
                      <th>智能体平台</th>
                      <th>每次请求收费(元)</th>
                      <th>余额</th>
                    </tr>
                    </thead>
                    <tbody>
                    <!-- 动态生成的智能体数据行 -->
                    </tbody>
                  </table>
                  <div class="pagination" id="paid-agent-pagination">
                    <!-- 分页按钮将动态添加到这里 -->
                  </div>
                </div>
                <!-- 我的订阅列表结束 -->

                <div class="user-material tab w-full" id="user-upload-file-container">
                  <div class="user-material-head">
                    <span>我的语料</span>
                  </div>
                  <div class="material-nav  w-full p-1">
                    <button class="material-title material-nav-active" id="link1"
                            onclick="changeMaterialPage(this)">
                      我的语料
                    </button>
                    <button class="material-title material-nav-not-active" id="link3"
                            onclick="changeMaterialPage(this)">
                      向量数据库
                    </button>
                    <a onclick="backToChat()">
                      <svg t="1740019605102" class="icon" viewBox="0 0 1024 1024" version="1.1"
                           xmlns="http://www.w3.org/2000/svg" p-id="9950" xmlns:xlink="http://www.w3.org/1999/xlink"
                           width="24" height="24">
                        <path
                                d="M555.6 692.8c-5.5 0-10.9-2.1-15.1-6.2L425 571c-25-25-25-65.7 0-90.7l115.5-115.5c8.3-8.3 21.8-8.3 30.2 0s8.3 21.8 0 30.2L455.2 510.5c-8.4 8.4-8.4 22 0 30.4l115.5 115.5c8.3 8.3 8.3 21.8 0 30.2-4.2 4.1-9.6 6.2-15.1 6.2z"
                                p-id="9951"></path>
                        <path
                                d="M512 42.7c258.8 0 469.3 210.5 469.3 469.3S770.8 981.3 512 981.3 42.7 770.8 42.7 512 253.2 42.7 512 42.7M512 0C229.2 0 0 229.2 0 512s229.2 512 512 512 512-229.2 512-512S794.8 0 512 0z"
                                p-id="9952"></path>
                      </svg>
                    </a>
                  </div>

                  <div id="upload-file-list">
                    <div id="my-corpus" class="material-item">
                      <div class="corpus-list corpus-container">
                        <!-- 创建知识库卡片 -->
                        <div id="addCorpus" class="corpus-card">
                            <i class="fa fa-plus-circle"></i>
                            <span>创建知识库</span>
                        </div>
            
                        <!-- 知识库卡片示例 -->
                        <div class="corpus-card corpus-activate">
                            <div class="corpus-card-title">默认知识库</div>
                            <div class="corpus-card-info">
                                <span><i class="fa fa-file-text-o"></i> 文件数: 128</span>
                                <span><i class="fa fa-calendar-o"></i> 创建时间: 2023-05-15</span>
                            </div>
                            <div class="corpus-operation">
                                <button class="edit-btn" onclick="enterCorpus(1, this)" title="进入知识库">
                                    进入
                                </button>
                                <button class="delete-btn" onclick="deleteCorpus(1)" title="删除知识库">
                                    删除
                                </button>
                            </div>
                        </div>
                    </div>
                    <div class="corpus-detail corpus-container" style="display:none">
                      <span style="font-size: 1rem; font-weight: 600; margin-top: 1rem; margin-bottom: 1rem;">${detail.name}</span>
                      
                      <div class="search-settings-form">
                        <form>
                          <div>
                            <label for="similarity-threshold">文档相似度阈值:</label>
                            <input type="number" id="similarity-threshold" min="0" max="1" step="0.01" value="0.8">
                          </div>
                          <div>
                            <label for="similarity-topk">文档相似度TopK:</label>
                            <input type="number" id="similarity-topk" min="1" max="100" value="10">
                          </div>
                          <div>
                            <label for="full-text-search">全文检索:</label>
                            <input type="checkbox" id="full-text-search">
                          </div>
                          <div>
                            <label for="llm-assistant">大语言模型辅助:</label>
                            <input type="checkbox" id="llm-assistant">
                          </div>
                          <div class="search-settings-save">
                            <button type="button" onclick="saveSearchSettings()" class="search-save-btn">保存设置</button>
                          </div>
                        </form>
                      </div>
                      
                      <div class="drop-area" id="dropArea">
                          <p>将文件拖放到这里或点击选择文件</p>
                          <input type="file" id="fileInput" />
                          <button class="button" onclick="triggerFileInput()">选择文件</button>
                      </div>
                      <div id="loadingSpinner" class="hidden"></div> <!-- 加载圈圈 -->
                      <div id="results_file" class="my-table">
                          <table id="upload-file-list1" class="" border="1">
                          <thead>
                          <tr>
                              <th>文件名称</th>
                              <th>文件</th>
                              <th>更新时间</th>
                              <th>操作</th>
                          </tr>
                          </thead>
                          <tbody>
                          <!-- 动态生成已上传文件数据 -->
                          </tbody>
                          </table>
                      </div>
                      <div class="pagination" id="file-upload-pagination">
                          <!-- 分页按钮将动态添加到这里 -->
                      </div>
                      </div>
                    </div>
                    <div id="vector-database" class="material-item " style="display: none;">
                      <div class="chat-settings">
                        <div class="chat-setting-item">
                          <!-- 知识库选择 -->
                          <div class="setting-section">
                            <div style="display: flex; align-items: center; justify-content: space-between;">
                              <h3 style="font-size: 1.4em; color: #023f63; margin: 0; width: 13.5em;">知识库选择</h3>
                              <div class="chat-setting-item-control">
                                <select id="knowledge-base-select" onchange="onKnowledgeBaseSelect()" style="width: 200px; padding: 8px; font-size: 1.1em; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;">
                                  <option value="">请选择知识库</option>
                                </select>
                              </div>
                            </div>
                          </div>
                          <div id="vector-settings-container" class="setting-section" style="display: none;">
                            <div style="display: flex; align-items: center; justify-content: space-between;">
                              <h3 style="font-size: 1.4em; color: #023f63; margin: 0; width: 13.5em;">文档相似度阈值</h3>
                              <div class="chat-setting-item-control">
                                <input type="range" id="distance" min="0" max="1" step="0.01" value="0.8"
                                       style="width: 200px; margin-right: 10px;" />
                                <span id="distance_value" style="font-size: 1.1em; width: 60px;">0.8</span>
                                <button onclick="submitSettings('distance')"
                                        class="setting-btn setting-submit-btn">提交</button>
                                &nbsp;&nbsp;
                                <button onclick="resetSlice('distance')"
                                        class="setting-btn setting-reset-btn">重置</button>
                              </div>
                            </div>
                          </div>
                          <!-- 向量上下文最大条数 -->
                          <div id="vector-max-top-container" class="setting-section" style="margin-top: 40px; display: none;">
                              <div style="display: flex; align-items: center; justify-content: space-between;">
                                <h3 style="font-size: 1.4em; color: #023f63; margin: 0; width: 13.5em;">向量上下文最大条数</h3>
                                <div class="chat-setting-item-control">
                                  <input type="number" id="vector-max-top" value="30"
                                         style="width: 80px; padding: 8px; font-size: 1.1em; margin-right: 10px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box;" />
                                  <button onclick="submitSettings('vector-max-top')"
                                          class="setting-btn setting-submit-btn">提交</button>
                                  &nbsp;&nbsp;
                                  <button onclick="resetSlice('vector-max-top')"
                                          class="setting-btn setting-reset-btn">重置</button>
                                </div>
                              </div>
                            </div>
                          <!-- 向量相似度搜索 -->
                          <div id="vector-search-container" class="setting-section" style="margin-top: 40px; display: none;">
                            <h3 style="font-size: 1.4em; margin-bottom: 15px; color: #023f63;">向量相似度搜索</h3>
                            <div class="relative">
                              <input type="text" id="searchText" placeholder="请输入查询内容..."
                                     style="width: 100%; max-width: 800px; padding: 12px; font-size: 16px; border-radius: 8px; border: 1px solid #ccc !important;">
                              <button onclick="search()" class="vector-search"></button>
                            </div>
                          </div>
                        </div>
                      </div>
                      <div id="results">
                        <!-- 结果项将动态加载到这里 -->
                      </div>
                      <!-- Modal -->
                      <div id="myModal"
                           style="display: none; position: fixed; z-index: 1; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.4); padding-top: 60px;">
                        <div
                                style="background-color: #fefefe; margin: 5% auto; border: 1px solid #888; width: 80%; max-width: 800px; border-radius: 8px;">
                          <div class="material-modal-title">
                                <span id="close_vector_detail"
                                      style="color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor: pointer;"
                                      onclick="closeVectorDetailModal()">&times;</span>
                          </div>
                          <div class="material-modal-content">
                            <h3 id="modalTitle_x" style="color: #004f99;"></h3>
                            <p><strong>文件ID:</strong> <span id="modalId"></span></p>
                            <p><strong>文件名:</strong> <span id="modalFilename"></span></p>
                            <p><strong>文件路径:</strong> <span id="modalFilepath"></span></p>
                            <p><strong>类别:</strong> <span id="modalCategory"></span></p>
                            <p><strong>距离:</strong> <span id="modalDistance"></span></p>
                            <p> <strong>内容:</strong></p>
                            <p id="modalDocument" style="font-size: 14px; color: #555;"></p>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="user-model tab w-full model-modules-box" id="user-model">
                  <div class="user-model-head">
                    <span>我的模型</span>
                  </div>
                  <div class="model-modules-nav w-full p-1">
                    <button class="model-modules-title model-modules-nav-active"
                            onclick="loadUserModule(this, 'user-finetune', 'loadFinetuneData')">
                      模型微调
                    </button>
                    <button class="model-modules-title model-modules-nav-not-active "
                            onclick="loadUserModule(this, 'user-develop', 'loadDevelopData')">
                      模型部署
                    </button>
                    <button class="model-modules-title model-modules-nav-not-active"
                            onclick="loadUserModule(this, 'user-manager', 'loadDevelopManagerData')">
                      模型管理
                    </button>

                    <button class="model-modules-title model-modules-nav-not-active"
                            onclick="loadUserModule(this, 'user-upload-model', 'loadUserUploadModelData')">
                      模型上传
                    </button>

                    <a onclick="backToChat()">
                      <svg t="1740019605102" class="icon" viewBox="0 0 1024 1024" version="1.1"
                           xmlns="http://www.w3.org/2000/svg" p-id="9950" xmlns:xlink="http://www.w3.org/1999/xlink"
                           width="24" height="24">
                        <path
                                d="M555.6 692.8c-5.5 0-10.9-2.1-15.1-6.2L425 571c-25-25-25-65.7 0-90.7l115.5-115.5c8.3-8.3 21.8-8.3 30.2 0s8.3 21.8 0 30.2L455.2 510.5c-8.4 8.4-8.4 22 0 30.4l115.5 115.5c8.3 8.3 8.3 21.8 0 30.2-4.2 4.1-9.6 6.2-15.1 6.2z"
                                p-id="9951"></path>
                        <path
                                d="M512 42.7c258.8 0 469.3 210.5 469.3 469.3S770.8 981.3 512 981.3 42.7 770.8 42.7 512 253.2 42.7 512 42.7M512 0C229.2 0 0 229.2 0 512s229.2 512 512 512 512-229.2 512-512S794.8 0 512 0z"
                                p-id="9952"></path>
                      </svg>
                    </a>
                  </div>
                  <div class="model-modules w-full p-1">
                    <div class="model-module finetune w-full hidden" id="user-finetune">
                      <div class="train-form" id="train-form">
                        <div class="train-datasets">
                          <div class="train-dataset-upload">
                            <label for="sel-model"><strong>选择模型</strong></label>
                            <select name="sel-model" id="sel-model">
                              <option value="Qwen1.5-7B">Qwen1.5-7B</option>
                              <option value="DeepSeek-R1-Distill-Qwen-1.5B">DeepSeek-R1-Distill-Qwen-1.5B</option>
                            </select>
                            <label for="up-datasets"><strong>上传数据集</strong></label>
                            <input name="up-datasets" type="file" id="up-datasets">
                            <button class="train-dataset-upload-btn" onclick="uploadUserDatasets()">
                              上传
                            </button>
                            <br>
                            <span id="up-datasets-status"></span>
                          </div>
                          <div class="train-dataset-sel">
                            <label for="sel-datasets"><strong>选择数据集</strong></label>
                            <sapn id="sel-datasets">
                            </sapn>
                            <button onclick="deleteUserDatasets()">
                              <img src="images/delete.png" alt="删除">
                              <span>删除</span>
                            </button>
                          </div>
                        </div>
                        <div class="train-args">
                          <div class="train-args-tile">
                            <strong>模型参数:</strong>
                          </div>
                          <div class="train-args-list">
                            <div class="train-args-row">
                              <label for="finetuning_type">训练方式</label>
                              <select name="finetuning_type">
                                <option value="full">full</option>
                                <option value="freeze">freeze</option>
                                <option value="lora" selected>lora</option>
                              </select>
                              <label for="stage">训练阶段</label>
                              <select name="stage" style="width: 10rem;">
                                <option value="sft" selected>Supervised Fine-Tuning</option>
                                <option value="rm">Reward Modeling</option>
                                <option value="ppo">PPO</option>
                                <option value="dpo">DPO</option>
                                <option value="kto">KTO</option>
                                <option value="kto">KTO</option>
                                <option value="pt">Pre-Training</option>
                              </select>

                            </div>
                            <div class="train-args-row">
                              <label for="quantization-bit">量化等级</label>
                              <select name="quantization-bit">
                                <option value="none" selected>none</option>
                                <option value="8">8</option>
                                <option value="4">4</option>
                              </select>
                              <label for="quantization-method">量化方法</label>
                              <select name="quantization-method">
                                <option value="none" selected>bitsandbytes</option>
                                <option value="hqq">hqq</option>
                                <option value="eetq">eetq</option>
                              </select>
                              <label for="template">对话模板</label>
                              <input name="template" id="template" value="" type="text">
                              <label for="RoPE-scaling">RoPE 插值方法</label>
                              <select name="RoPE-scaling">
                                <option value="none" selected>none</option>
                                <option value="linear">linear</option>
                                <option value="dynamic">dynamic</option>
                                <option value="yarn">yarn</option>
                                <option value="llama3">llama3</option>
                              </select>
                              <label for="booster">加速方式</label>
                              <select name="booster" type="text">
                                <option value="auto">auto</option>
                                <option value="flashattn2">flashattn2</option>
                                <option value="unsloth">unsloth</option>
                                <option value="liger_kernel">liger_kernel</option>
                              </select>
                            </div>
                            <div class="train-args-row">
                              <label for="learning-rate">学习率</label>
                              <input name="learning-rate" type="text" value="0.0001">
                              <label for="epochs">训练轮数</label>
                              <input name="epochs" type="text" value="3">
                              <label for="maximum-gradient-norm">最大梯度范数</label>
                              <input name="maximum-gradient-norm" type="text" value="1">
                              <label for="max-samples">最大样本数</label>
                              <input name="max-samples" type="text" value="100000">
                              <label for="compute-type">计算类型</label>
                              <select name="compute-type">
                                <option value="bf16">bf16</option>
                                <option value="fp16">fp16</option>
                                <option value="fp32">fp32</option>
                                <option value="pure_bf16">pure_bf16</option>
                              </select>
                            </div>
                            <div class="train-args-row">
                              <label for="cutoff-length">截断长度</label>
                              <input name="cutoff-length" type="text" value="1024">
                              <label for="batch-size">批处理大小</label>
                              <input name="batch-size" type="text" value="1">
                              <label for="gradient-accumulation">梯度累积</label>
                              <input name="gradient-accumulation" type="text" value="2">
                              <label for="val-size">验证集比例</label>
                              <input name="val-size" type="text" value="0.1">
                              <label for="LR-scheduler">学习率调节器</label>
                              <select name="LR-scheduler" type="text">
                                <option value="linear">linear</option>
                                <option value="cosine" selected>cosine</option>
                                <option value="cosine_with_restarts">cosine_with_restarts</option>
                                <option value="polynomial">polynomial</option>
                                <option value="constant">constant</option>
                                <option value="constant_with_warmup">constant_with_warmup</option>
                                <option value="inverse_sqrt">inverse_sqrt</option>
                                <option value="reduce_lr_on_plateau">reduce_lr_on_plateau</option>
                                <option value="cosine_with_min_lr">cosine_with_min_lr</option>
                                <option value="warmup_stable_decay">warmup_stable_decay</option>
                              </select>
                            </div>
                            <div class="collapsible-container">
                              <div class="collapsible-header">
                                <span>额外配置</span>
                                <span class="icon">▼</span>
                              </div>
                              <div class="collapsible-content">
                                <div class="collapsible-content-inner">
                                  <div class="train-args-row">
                                    <label for="logging_steps">Logging steps</label>
                                    <input style="width: 3rem;" name="logging_steps" type="text" value="5">
                                    <label for="save_steps">Save steps</label>
                                    <input style="width: 3rem;" name="save_steps" type="text" value="1">
                                    <label for="warmup_steps">Warmup steps</label>
                                    <input style="width: 3rem;" name="warmup_steps" type="text" value="0">
                                    <label for="NEFTune_alpha">NEFTune alpha</label>
                                    <input style="width: 3rem;" name="NEFTune_alpha" type="text" value="0">
                                    <label for="Extra arguments">Extra arguments</label>
                                    <input style="width: 10rem;" name="extra_arguments" type="text" value='{"optim": "adamw_torch"}'>
                                  </div>
                                  <div class="train-args-row">
                                    <label for="enable_thinking ">Enable thinking</label>
                                    <input name="enable_thinking" type="checkbox" >
                                  </div>
                                </div>
                              </div>
                            </div>
                            <div class="collapsible-container">
                              <div class="collapsible-header">
                                <span>freeze 配置</span>
                                <span class="icon">▼</span>
                              </div>
                              <div class="collapsible-content">
                                <div class="collapsible-content-inner">
                                  <div class="train-args-row">
                                    <label for="freeze_trainable_layers">Trainable layers</label>
                                    <input style="width: 3rem;" name="freeze_trainable_layers" type="text" value="2">
                                    <label for="freeze_trainable_modules">Trainable modules</label>
                                    <input style="width: 10rem;" name="freeze_trainable_modules" type="text" value="all">
                                    <label for="freeze_extra_modules">Extra modules (optional)</label>
                                    <input style="width: 10rem;" name="freeze_extra_modules" type="text" value="">

                                  </div>
                                </div>
                              </div>
                            </div>

                            <div class="collapsible-container">
                              <div class="collapsible-header">
                                <span>LoRA 配置</span>
                                <span class="icon">▼</span>
                              </div>
                              <div class="collapsible-content">
                                <div class="collapsible-content-inner">
                                  <div class="train-args-row">
                                    <label for="lora_rank">LoRA rank</label>
                                    <input style="width: 3rem;" name="lora_rank" type="text" value="">
                                    <label for="lora_alpha">LoRA alpha</label>
                                    <input style="width: 3rem;" name="lora_alpha" type="text" value="">
                                    <label for="lora_dropout">LoRA dropout</label>
                                    <input style="width: 3rem;" name="lora_dropout" type="text" value="0">
                                    <label for="loraplus_lr_ratio">LoRA+ LR ratio</label>
                                    <input style="width: 3rem;" name="loraplus_lr_ratio" type="text" value="">
                                    <label for="create_new_adapter ">Create new adapter</label>
                                    <input name="create_new_adapter" type="checkbox">
                                  </div>
                                  <div class="train-args-row">


                                    <label for="use_rslora ">Use rslora</label>
                                    <input name="use_rslora" type="checkbox">
                                    <label for="use_dora ">Use DoRA</label>
                                    <input name="use_dora" type="checkbox">
                                    <label for="pissa_init ">Use PiSSA</label>
                                    <input name="pissa_init" type="checkbox">
                                    <label for="lora_target">LoRA modules (optional)</label>
                                    <input style="width: 6rem;" name="lora_target" type="text" value="q_proj,v_proj">
                                    <label for="additional_target">Additional modules (optional)</label>
                                    <input style="width: 6rem;" name="additional_target" type="text" value="">
                                  </div>
                                </div>
                              </div>
                            </div>
                            <div class="collapsible-container">
                              <div class="collapsible-header">
                                <span>RLHF 配置</span>
                                <span class="icon">▼</span>
                              </div>
                              <div class="collapsible-content">
                                <div class="collapsible-content-inner">
                                  <div class="train-args-row">
                                    <label for="pref_beta">Beta value</label>
                                    <input style="width: 3rem;" name="pref_beta" type="text" value="0.1">
                                    <label for="pref_ftx">Ftx gamma</label>
                                    <input style="width: 3rem;" name="pref_ftx" type="text" value="0">
                                    <label for="pref_loss">Loss type</label>
                                    <select name="pref_loss" type="text">
                                      <option value="sigmoid">sigmoid</option>
                                      <option value="hinge">hinge</option>
                                      <option value="ipo">ipo</option>
                                      <option value="kto_pair">kto_pair</option>
                                      <option value="orpo">orpo</option>
                                      <option value="simpo" selected>simpo</option>
                                    </select>
                                    <label for="ref_model">Reward model</label>
                                    <input style="width: 3rem;" name="ref_model" type="text" value="">
                                  </div>
                                  <div class="train-args-row">
                                    <label for="ppo_score_norm ">Score norm</label>
                                    <input name="ppo_score_norm" type="checkbox">
                                    <label for="ppo_whiten_rewards ">Whiten rewards</label>
                                    <input name="ppo_whiten_rewards" type="checkbox">
                                  </div>
                                </div>
                              </div>
                            </div>
                            <!-- <div>
                        <label for="datasets">检查点路径</label>
                        <input name="model" type="text">
                      </div> -->
                            <div class="train-btn-box">
                              <p class="train-btn-box-mid">
                                <label for="output-dir"><strong>保存地址</strong></label>
                                <input name="output-dir" type="text" value="">
                                <button onclick="doTrain(this)">
                                  <image src="images/train.png" />训练
                                </button>
                              </p>
                            </div>

                          </div>
                        </div>
                      </div>
                      <div class="train-view" id="train-view">
                        <div class="train-view-title"><strong>训练日志</strong> </div>
                        <div class="train-loss w-full">
                          <canvas id="loss-canvas" width="200" , height="200"></canvas>
                        </div>
                        <div class="train-view-content" id="train-view-content"></div>
                      </div>
                    </div>
                    <div class="model-module user-develop hidden" id="user-develop">
                      <div class="develop-model-container">
                        <button id="addModeldevelop-btn" class="develop-btn">
                          <img src="images/add.png" alt="addDevelop">
                          <strong>部署模型</strong>
                        </button>
                        <div id="modelList" class="model-list">
                        </div>
                      </div>

                      <!-- 模态框 -->
                      <div id="myDevelop" class="develop">
                        <div class="develop-content">
                          <div class="develop-content-title">
                            <span class="close">&times;</span>
                            <span>部署模型</span>
                          </div>

                          <form id="modelForm">
                            <label
                                    for="modelPath"><strong>模型路径:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</strong></label>
                            <input type="text" id="modelPath" name="modelPath" placeholder="请输入" required>
                            <br>
                            <label for="adapterPath"><strong>Adapter路径:</strong> </label>
                            <input type="text" id="adapterPath" name="adapterPath" placeholder="请输入">
                            <button type="submit" class="develop-btn">发布模型</button>
                          </form>
                        </div>
                      </div>
                    </div>
                    <div class="model-module manager hidden" id="user-manager">
                      <div class="modal-container my-table">
                        <button id="addModelBtn" class="develop-btn">
                          <img src="images/add.png" alt="addDevelop">
                          <strong>添加模型</strong>
                        </button>
                        <table id="modelTable">
                          <thead>
                          <tr>
                            <th>模型名</th>
                            <th>是否线上模型</th>
                            <th>API Key</th>
                            <th>模型类型</th>
                            <th>模型终端地址</th>
                            <th>状态</th>
                            <th>操作</th>
                          </tr>
                          </thead>
                          <tbody>
                          <!-- 模型行将在这里动态添加 -->
                          </tbody>
                        </table>
                      </div>

                      <div id="modelModal" class="modal-manager">
                        <div class="modal-manager-content">
                          <!-- <span class="close">&times;</span>
                        <h2 id="modalTitle">添加模型</h2> -->
                          <div class="modal-content-title">
                            <span class="close">&times;</span>
                            <span>添加模型</span>
                          </div>
                          <form id="modeManagerlForm">
                            <div class="modal-manager-row">
                              <label for="modelName"><strong>模型名:</strong></label>
                              <input type="text" id="modelName" name="modelName" placeholder="请输入" required>
                            </div>
                            <div class="modal-manager-row">
                              <label for="isOnline"><strong>是否线上模型:</strong></label>
                              <input type="checkbox" id="isOnline" name="isOnline">
                            </div>
                            <div id="onlineFields" style="display: none;">
                              <div class="modal-manager-row">
                                <label for="apiKey"><strong>API Key:</strong></label>
                                <input type="text" id="apiKey" name="apiKey" placeholder="请输入">
                              </div>
                              <div class="modal-manager-row">
                                <label for="modelType"><strong>模型类型:</strong></label>
                                <select id="modelType" name="modelType">
                                </select>
                              </div>
                            </div>
                            <div class="modal-manager-row" id="offlineFields">
                              <label for="modelEndpoint"><strong>模型终端地址:</strong></label>
                              <input type="text" id="modelEndpoint" name="modelEndpoint" placeholder="请输入">
                            </div>
                            <div class="modal-manager-row">
                              <button type="submit">保存修改</button>
                            </div>
                          </form>
                        </div>
                      </div>
                    </div>

                    <div class="model-module user-upload-model hidden" id="user-upload-model">
                      <div class="upload-container">
                        <div class="file-drop-area" id="file-drop-area">
                          <div class="file-drop-icon">
                            <i class="fa fa-file-o"></i>
                          </div>
                          <h3 class="file-drop-title">拖放文件到此处上传</h3>
                          <p class="file-drop-or">或</p>
                          <label class="file-drop-button">
                            <i class="fa fa-plus mr-2"></i>选择文件
                            <input type="file" id="file-input" class="hidden" multiple>
                          </label>
                          <p class="file-drop-note">支持断点续传</p>
                        </div>

                        <div class="upload-queue" id="upload-queue">
                          <div class="upload-queue-empty">
                            <i class="fa fa-folder-open-o"></i>
                            <p>暂无上传任务</p>
                          </div>
                        </div>

                        <div class="upload-settings">
                          <div class="upload-settings-title">
                            <i class="fa fa-cog"></i>上传设置
                          </div>
                          <div class="upload-settings-fields">
                            <div class="upload-settings-field">
                              <label class="upload-settings-label" for="chunk-size">分片大小</label>
                              <select id="chunk-size" class="upload-settings-select">
                                <option value="1">1MB</option>
                                <option value="2" selected>2MB</option>
                                <option value="5">5MB</option>
                                <option value="10">10MB</option>
                                <option value="20">20MB</option>
                                <option value="50">50MB</option>
                                <option value="100">100MB</option>
                              </select>
                            </div>
                            <div class="upload-settings-field">
                              <label class="upload-settings-label" for="concurrency">并发上传数</label>
                              <select id="concurrency" class="upload-settings-select">
                                <option value="1">1个</option>
                                <option value="2" selected>2个</option>
                                <option value="3">3个</option>
                                <option value="5">5个</option>
                                <option value="10">10个</option>
                              </select>
                            </div>
                          </div>
                        </div>

                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div id="conTab" class="react-scroll-to-bottom--css-dlyqs-79elbk h-full dark:bg-gray-800">
                <div class="react-scroll-to-bottom--css-dlyqs-1n7m0yu h-full">
                  <div class="flex flex-col text-sm dark:bg-gray-800 h-full">
                    <div class="flex h-full flex-col items-center justify-between">

                      <div id="item-content" class="w-full" style="overflow: auto;">
                        <div id="topTitle"
                             class="relative align-center flex w-full md:flex-col justify-center self-center ">
                          <div class="absolute right-2 top-2 text-gray-600 text-sm sm:none">
                            内容由AI协助
                          </div>
                          <div id="centerTitleBox" class="center-title-box" style="margin-left: auto; margin-right: auto;">
                            <h1 class="w-full"></h1>
                            <div class="mobile_logo_container"></div>
                            <!-- <canvas id="title-canvas"
                                    style="width: 300px; height: 120px; margin-top: 10px;"></canvas> -->
                          </div>
                        </div>
                        <div id="ball-container" class="relative ball-container">
                          <div class="ball-mask"
                               style="width: min(47em, 90%); height: 0px; position: relative; margin-left: auto; margin-right: auto;">
                            <div class="ball-corner-container" id ="ball-corner-container"
                                 style=" z-index: 5; width: 100%;position: absolute; margin-left: auto; margin-right: auto;">
                              <div class="ball-left-top absolute ball-corner">
                                <ul>
                                </ul>
                              </div>
                              <div class="ball-right-top absolute ball-corner">
                                <ul>
                                </ul>
                              </div>
                            </div>
                          </div>
                          <div id="ball-div">

                          </div>
                        </div>

                        <!-- ***********************介绍框 start******************************** -->
                        <div id="introduces" class="relative">
                          <div class="h-full flex gap-0 md:gap-2 justify-center">
                            <div class="grow">
                              <div
                                      class="absolute left-0 mb-4 flex w-full grow gap-2  sm:pb-0 md:static md:mb-0 md:max-w-none">
                                <div class="grid w-full grid-flow-row grid-cols-2 gap-3"></div>
                              </div>
                            </div>
                          </div>
                        </div>
                        <!-- ***********************介绍框 end******************************** -->

                    </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div id="not-content"
                 class="absolute bottom-0 left-0  border-t md:border-t-0 dark:border-white/20 md:border-transparent md:dark:border-transparent md:bg-vert-light-gradient bg-white dark:bg-gray-800 md:!bg-transparent dark:md:bg-vert-dark-gradient pt-2 md:w-[calc(100%-.5rem)]">
              
              
              <form
                      class="stretch mx-2 flex flex-row gap-3 last:mb-2 md:mx-4 md:last:mb-6 lg:mx-auto lg:max-w-2xl xl:max-w-3xl">
                <div class="relative flex h-full flex-1 items-stretch md:flex-col">
                  <!-- ***********************输入框******************************** -->
                  <div class="flex w-full " id="queryBox" class="border border-black/10">
                    <!-- 智能体功能列表 -->
                    <div
                            class="flex flex-col w-full py-2 flex-grow md:py-3 md:pl-4 relative  bg-white dark:border-gray-900/50 dark:text-white dark:bg-gray-700 rounded-md shadow-[0_0_10px_rgba(0,0,0,0.10)] dark:shadow-[0_0_15px_rgba(0,0,0,0.10)]">
                          <textarea id="queryContent" data-agent="" tabindex="0" data-id="root" rows="1"
                                    placeholder="请输入文字..."
                                    class=" m-0 w-full resize-none border-0 bg-transparent p-0 pl-2 pr-7 focus:ring-0 focus-visible:ring-0 dark:bg-transparent md:pl-0"></textarea>
                      <!-- <svg style="width:30px;height:24px" t="1694769456924" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="8678" width="200" height="200"><path d="M801.171 483.589H544V226.418c0-17.673-14.327-32-32-32s-32 14.327-32 32v257.171H222.83c-17.673 0-32 14.327-32 32s14.327 32 32 32H480v257.17c0 17.673 14.327 32 32 32s32-14.327 32-32v-257.17h257.171c17.673 0 32-14.327 32-32s-14.327-32-32-32z" fill="" p-id="8679"></path></svg> -->
                      <!-- ******************发送按钮开始 ***************************** -->
                      <!-- bg-gray-900 -->
                      <button  type="button" id="addButton" style="left: 0.75rem;"
                        class="absolute p-1 rounded-md text-gray-500   hover:bg-gray-100 dark:hover:text-gray-400 dark:hover:bg-ldbj-900 disabled:hover:bg-transparent dark:disabled:hover:bg-transparent">
                      <img style="position: relative;" src="images/upload.png">
                      </button>
                      <button  type="button" id="agentButton" style="left: 3.25rem;"
                        class="absolute p-1 rounded-md text-gray-500   hover:bg-gray-100 dark:hover:text-gray-400 dark:hover:bg-ldbj-900 disabled:hover:bg-transparent dark:disabled:hover:bg-transparent">
                        <div id="agent-container" class="absolute flex w-full flex-1 items-stretch md:flex-col hidden">
                          <div id="agentList" class="absolute left-0 z-50 agent-pannel w-32 ">
                            <div id="agent-head" class="agent-head  pt-2 pb-1 text-center">智能体</div>
                            <!-- <ul id = "agent-tools" class="pb-2" style="max-height: 100px; overflow: auto"> -->
                            <ul id="agent-tools" class="pb-2">
                              <li class=" pl-5  not-available " onclick="openAgentModal(event)">发布智能体</li>
                              <li class=" pl-5  not-available " onclick="openCreateAgent()">创建智能体</li>
                            </ul>
                          </div>
                        </div>
                        <img style="position: relative;" src="images/agent_btn.png">
                      </button>
                      <!-- <button  type="button" id="agentButton"
                        class="absolute p-1 rounded-md text-gray-500 bottom-1.5 right-1 md:bottom-2.5  hover:bg-gray-100 dark:hover:text-gray-400 dark:hover:bg-ldbj-900 disabled:hover:bg-transparent dark:disabled:hover:bg-transparent">
                      <img style="position: relative;width: 25px;height: 25px;" src="images/rj.png">
                      </button> -->
                      <button  type="button" id="voiceIcon"
                        class="absolute p-1 rounded-md text-gray-500  right-16  hover:bg-gray-100 dark:hover:text-gray-400 dark:hover:bg-ldbj-900 disabled:hover:bg-transparent dark:disabled:hover:bg-transparent">
                        <img style="position: relative;" src="images/vioce.png">
                      </button>
                      
                      <button onclick="textQuery()" type="button" id="queryBtn"
                              class="absolute p-1 rounded-md text-gray-500  right-1  hover:bg-gray-100 dark:hover:text-gray-400 dark:hover:bg-ldbj-900 disabled:hover:bg-transparent dark:disabled:hover:bg-transparent">
                        <img style="position: relative;" src="images/send.png">
                      </button>
                      <!-- ******************发送按钮关闭***************************** -->
                    </div>

                  </div>
                  <!-- ***********************输入框end******************************** -->
                </div>
              </form>

              <!-- *********************** 底部******************************** -->
              <div id="footer-info" class="relative">
                <div
                        class="relative pb-3 pt-2 text-center md:text-xs text-[10px] text-gray-600 dark:text-gray-300 md:px-[60px] md:pb-6 md:pt-3"
                        style="max-height: 50px;">
                  <div class="lg:mx-auto lg:max-w-2xl xl:max-w-3xl" style="max-width: 60rem; max-height: 50px;">
                    <a href="http://beian.miit.gov.cn/" target="_blank"
                       style="display:inline-block">京ICP备&nbsp;2020046697号</a>&nbsp;&nbsp;&nbsp;&nbsp;
                    <img style="width: 20px; height: 20px; display: inline-block;" src="images/beian.png" alt="">
                    <a href="http://www.beian.gov.cn/portal/registerSystemInfo?recordcode=11010802033940"
                       target="_blank" style="display:inline-block">京公网按备号&nbsp;11010802033940</a>
                    &nbsp;&nbsp;&nbsp;&nbsp; 联系邮箱:
                    <a href="mailto:service@landingbj.com" style="text-decoration: none;">service@landingbj.com</a>
                    &nbsp;&nbsp;&nbsp;&nbsp;联系电话: 027-87659116
                    &nbsp;&nbsp;&nbsp;&nbsp;<div>Powered By <a style="color: rgb(35 142 252);"
                                                               href="https://github.com/landingbj/lagi">Lag[i]</a>&nbsp;一种通用人工智能的实现验证</div>
                  </div>
                </div>

                <div id="help-button" class="group fixed bottom-5 right-4 z-10 flex flex-row items-center gap-3">
                  <div class="hidden md:block">
                    <div class="group relative" data-headlessui-state="">
                      <button
                              class="flex items-center justify-center rounded-full border border-gray-200 bg-gray-50 text-gray-600 dark:border-white/10 dark:bg-white/10 dark:text-gray-200"
                              id="headlessui-menu-button-:rh:" type="button" aria-haspopup="true" aria-expanded="false"
                              data-headlessui-state="">
                        <div class="h-6 w-6">?</div>
                      </button>
                    </div>
                  </div>
                </div>

              </div>
              <!-- *********************** 底部end******************************** -->
            </div>
          </div>



        </main>
      </div>
    </div>
  </div>
  <div class="absolute left-0 right-0 top-0 z-[2]"></div>
</div>
<div portal-container=""><span
        class="pointer-events-none fixed inset-0 z-[60] mx-auto my-2 flex max-w-[560px] flex-col items-stretch justify-start md:pb-5"></span>
</div>

<!-- 登录注册弹框开始 -->
<div id="overlay" class="overlay">
  <div class="modal" id="login-form">
    <div class="close-modal-container" style="position: absolute; right: 10px; z-index: 100;">
      <button class="close-modal" onclick="closeModal()"
              style="padding: 10px 15px; font-size: 24px; background-color: transparent; border: none; color: #333; cursor: pointer; transition: color 0.3s;">
        &times;
      </button>
    </div>
    <h2 style="display: flex; align-items: center; position: relative; width: 100%; justify-content: center;">
      <img src="images/Small_logo.png" alt="公司 Logo" class="modal-Small_logo" style="max-height: 50px;">
    </h2>
    <div
            style="display: flex; flex-direction: column; align-items: center; width: 80%; max-width: 350px; margin: 0 auto;">
      <input type="text" placeholder="用户名" id="login-username"
             style="width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 5px; font-size: 16px; box-sizing: border-box;">
      <input type="password" placeholder="密码" id="login-password"
             style="width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 5px; font-size: 16px; box-sizing: border-box;">
      <div class="captcha-container" style="display: flex; align-items: center; width: 100%;">
        <input type="text" placeholder="验证码" id="login-captcha"
               style="flex: 1; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 5px; font-size: 16px; box-sizing: border-box;">
        <img alt="验证码" class="login-captcha-image" onclick="updateCaptcha(this)"
             style="width: 30%; margin-left: 10px; cursor: pointer;">
      </div>
    </div>
    <div id="login-error" class="error-message">登录失败，请检查输入信息</div>
    <div
            style="display: flex; justify-content: center; gap: 15px; width: 80%; max-width: 350px; margin: 0 auto; margin-top: 20px; align-items: center;">
      <button onclick="submitLogin()"
              style="padding: 10px 20px; background-color: #4CAF50; color: white; border: none; border-radius: 5px; cursor: pointer; width: 100%;">
        登录
      </button>
      <button onclick="showRegisterPage()"
              style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 5px; cursor: pointer; width: 100%;">
        注册
      </button>
    </div>
  </div>
  <div class="modal login-hidden" id="register-form">
    <div class="close-modal-container" style="position: absolute; right: 10px; z-index: 100;">
      <button class="close-modal" onclick="closeModal()"
              style="padding: 10px 15px; font-size: 24px; background-color: transparent; border: none; color: #333; cursor: pointer; transition: color 0.3s;">
        &times;
      </button>
    </div>
    <h2 style="display: flex; align-items: center; position: relative; width: 100%; justify-content: center;">
      <img src="images/Small_logo.png" alt="公司 Logo" class="modal-Small_logo" style="max-height: 50px;">
    </h2>
    <div
            style="display: flex; flex-direction: column; align-items: center; width: 80%; max-width: 350px; margin: 0 auto;">
      <input type="text" placeholder="用户名" id="register-username"
             style="width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 5px; font-size: 16px; box-sizing: border-box;">
      <input type="password" placeholder="密码" id="register-password"
             style="width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 5px; font-size: 16px; box-sizing: border-box;">
      <input type="password" placeholder="确认密码" id="register-confirm-password"
             style="width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 5px; font-size: 16px; box-sizing: border-box;">
      <div class="captcha-container" style="display: flex; align-items: center; width: 100%;">
        <input type="text" placeholder="验证码" id="register-captcha"
               style="flex: 1; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 5px; font-size: 16px; box-sizing: border-box;">
        <img alt="验证码" class="register-captcha-image" onclick="updateCaptcha(this)"
             style="width: 30%; margin-left: 10px; cursor: pointer;">
      </div>
    </div>
    <div id="register-error" class="error-message">注册失败，请检查输入信息</div>
    <div
            style="display: flex; justify-content: center; gap: 15px; width: 80%; max-width: 350px; margin: 0 auto; margin-top: 20px; align-items: center;">
      <button onclick="submitRegister()"
              style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 5px; cursor: pointer; width: 100%;">
        注册
      </button>
      <button onclick="showLoginPage()"
              style="padding: 10px 20px; background-color: #4CAF50; color: white; border: none; border-radius: 5px; cursor: pointer; width: 100%;">
        返回登录
      </button>
    </div>
  </div>
</div>
<!-- 登录注册弹框结束 -->

<!-- 新增/编辑智能体弹窗开始 -->
<div id="agent-form-modal" class="modal">
  <div class="modal-content">
    <span class="close-btn" onclick="closeAgentModal()">&times;</span>
    <form id="agent-form">
      <label for="agent-name">智能体名称：</label>
      <input type="text" id="agent-name" required>
      <label for="platform">智能体平台：</label>
      <select id="platform" required>
        <option value="ai.agent.chat.qianfan.XiaoxinAgent">千帆</option>
        <option value="ai.agent.chat.coze.CozeAgent">扣子</option>
        <option value="ai.agent.chat.tencent.YuanQiAgent">元器</option>
        <option value="ai.agent.chat.zhipu.ZhipuAgent">智谱</option>
        <option value="ai.agent.customer.GeneralAgent">联动北方</option>
      </select>
      <label for="token">智能体Token：</label>
      <input type="text" id="token" required>
      <label for="app-id">智能体App ID：</label>
      <input type="text" id="app-id" required>
      <div style="display: flex; align-items: center;">
        <label>是否收费：</label>
        <label for="isFeeRequiredYes" style="margin-right: 5px;">是</label>
        <input type="radio" id="isFeeRequiredYes" name="isFeeRequired" value="true"
               style="width: 1px; margin-bottom: -0.4em;">

        <label for="isFeeRequiredNo" style="margin-right: 5px; padding-left: 10px;">否</label>
        <input type="radio" id="isFeeRequiredNo" name="isFeeRequired" value="false"
               style="width: 1px; margin-bottom: -0.4em;" checked>
      </div>
      <div id="pricePerReqContainer" style="display: none;">
        <label for="pricePerReq">每次请求收费(元)：</label>
        <input type="number" id="pricePerReq" step="0.01" min="0">
      </div>
      <button type="button" onclick="saveAgent()">发布</button>
    </form>
  </div>
</div>
<!-- 新增/编辑智能体弹窗结束 -->

<!-- 微信支付弹窗开始 -->
<div id="wechat_pay_qr" style="display: none;">
  <div class="qrCodeDiv">
    <button class="closePayBtn" onclick="cancelPayment()">&times;</button>
    <!-- 添加公司logo -->
    <div class="companyLogoDiv">
      <img class="company_logo" src="images/company_logo.png" alt="公司 Logo" />
    </div>
    <div class="qrDiv">
      <img id="qrCode" src="" />
    </div>
    <div id="wechatTitle">
      <img class="wechat_logo" src="images/wechat_logo.png" />
      <div class="wechat_label">
        微信支付 <span id="payAmount" class="orangeFont"></span> 元
      </div>
    </div>
    <!-- 添加取消支付按钮 -->
    <button id="h5PrepayBtn" class="paymentBtn" onclick="h5Prepay()">微信支付</button>
  </div>
</div>
<!-- 微信支付弹窗结束 -->

<!-- 查看收费智能体弹窗开始 -->
<div class="recharge-overlay" id="recharge-modal">
  <div class="recharge-modal">
    <button class="recharge-close-btn" id="recharge-close-btn" onclick="closeRechargeModal()">&times;</button>

    <div class="recharge-modal-header">
      <h2 class="recharge-agent-name">智能体名称：智能体A</h2>
    </div>

    <div class="recharge-modal-body">
      <div class="recharge-left">
        <p>单次调用价格</p>
        <span class="recharge-price">¥ 0.01</span>
      </div>

      <div class="recharge-right">
        <label for="recharge-call-count">可用次数</label>
        <input type="number" id="recharge-call-count" value="1" min="1" step="1">
      </div>
    </div>

    <div class="recharge-footer">
      <button class="recharge-subscribe-btn" id="recharge-subscribe-btn" onclick="subscription()">
        <span id="recharge-total-amount-footer">¥ 0.01</span> 订阅
      </button>
    </div>
  </div>
</div>
<!-- 查看收费智能体弹窗结束 -->

<!-- 创建智能体弹窗开始 -->
<div id="createLagiAgent" class="create_LagiAgent">
  <div class="createLagiAgent-content">
    <button class="close-btn" onclick="closeCreateAgent()">×</button>
    <h2>创建智能体</h2>
    <form id="createLagiAgentForm">
      <label for="LagiAgentName">名称 <span style="color: red">*</span></label>
      <input type="text" id="LagiAgentName" placeholder="请输入智能体名称">

      <label for="LagiAgentDescribe">设定 <span style="color: red">*</span></label>
      <textarea id="LagiAgentDescribe"
                placeholder="你是一个健身教练AI助手，主要职责是根据用户的需求制定个性化的健身计划和饮食建议。你需要熟练掌握相关领域的知识，并根据用户需求提供准确、专业、权威的解答。"></textarea>

      <div class="create_LagiAgentButtons">
        <button type="button" id="createLagiAgentButtons" onclick="createLagiAgent()" disabled>立即创建</button>
      </div>
    </form>
  </div>
</div>

<!-- 创建智能体弹窗结束 -->
<!-- 编排智能体弹窗结束 -->
<div id="orchestration-modal-container" class="orchestration-modal-container">
  <!-- 模态框区域 -->
  <div id="orchestration-modal" class="orchestration-modal">
    <div class="orchestration-modal-header">
      <span>编排</span>
      <button class="orchestration-add-btn" onclick="addRow()">
        <svg viewBox="0 0 24 24">
          <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" />
        </svg>
        添加
      </button>
    </div>
    <div class="orchestration-modal-body">
      <table id="orchestration-table" class="orchestration-table">
        <thead>
        <tr>
          <th class="orchestration-task">任务</th>
          <th class="orchestration-logic">逻辑</th>
          <th>节点类型</th>
          <th>结构类型</th>
          <th>操作</th>
        </tr>
        </thead>
        <tbody>
        <!-- 动态添加的行 -->
        </tbody>
      </table>
    </div>
    <div class="orchestration-modal-footer">
      <button class="orchestration-save-btn" onclick="saveOrchestration()">保存更改</button>
      <button class="orchestration-cancel-btn" onclick="closeOrchestrationAgent()">取消</button>
    </div>
  </div>

  <!-- 画布区域 -->
  <div id="orchestration-canvas-container" class="orchestration-canvas-container">
    <svg id="orchestration-canvas" class="orchestration-canvas"></svg>
  </div>
</div>
<!-- 编排智能体弹窗结束 -->

<!-- 创建/编辑知识库模态框 -->
<div id="corpusModal" class="corpus-modal">
  <div class="corpus-modal-content">
      <div class="corpus-modal-header">
          <h3 id="modalTitle">创建知识库</h3>
          <button class="corpus-modal-close" onclick="closeCorpusModal()">
              <i class="fa fa-times"></i>
          </button>
      </div>
      <div class="corpus-modal-body">
          <form id="corpusForm">
              <input type="hidden" id="corpusId" value="">
              <div class="form-group">
                  <label for="corpusName">知识库名称</label>
                  <input type="text" id="corpusName" class="form-control" placeholder="请输入知识库名称" required>
              </div>
              <div class="form-group">
                  <label for="corpusDesc">知识库描述</label>
                  <textarea id="corpusDesc" class="form-control" rows="3" placeholder="请输入知识库描述"></textarea>
              </div>
          </form>
      </div>
      <div class="corpus-modal-footer">
          <button class="btn btn-secondary" onclick="closeCorpusModal()">取消</button>
          <button class="btn btn-primary" onclick="saveCorpus()">保存</button>
      </div>
  </div>
</div>

<!-- 删除确认模态框 -->
<div id="deleteModal" class="corpus-modal">
  <div class="corpus-modal-content">
      <div class="corpus-modal-header">
          <h3>删除知识库</h3>
          <button class="corpus-modal-close" onclick="closeDeleteModal()">
              <i class="fa fa-times"></i>
          </button>
      </div>
      <div class="corpus-modal-body">
          <p>确定要删除这个知识库吗？此操作不可撤销，所有相关文件和数据都将被永久删除。</p>
          <input type="hidden" id="deleteCorpusId" value="">
      </div>
      <div class="corpus-modal-footer">
          <button class="btn btn-secondary" onclick="closeDeleteModal()">取消</button>
          <button class="btn btn-danger" onclick="confirmDelete()">确认删除</button>
      </div>
  </div>
</div>

<!-- 通知消息 -->
<div id="notification" class="notification">
  <i id="notificationIcon" class="fa fa-check-circle"></i>
  <span id="notificationMessage"></span>
</div>

<!-- mobile debug 插件 -->
<!-- <script src="https://unpkg.com/vconsole@latest/dist/vconsole.min.js"></script> -->
<script src="libs/jquery-3.1.1.min.js"></script>
<script src="js/fingerprint2.min.js"></script>
<script src="js/marked.min.js"></script>
<script src="libs/marked.min.js"></script>
<script src="libs/echart.min.js"></script>
<script src="libs/sse.js?ver=${initParam.version}"></script>
<script src="js/config.js?ver=${initParam.version}"></script>
<script src="js/common.js?ver=${initParam.version}"></script>
<script src="js/file.js?ver=${initParam.version}"></script>
<script src="js/hello.js?ver=${initParam.version}"></script>
<script src="js/chat.js?ver=${initParam.version}"></script>
<script src="js/conversations.js?ver=${initParam.version}"></script>
<script src="js/nav.js?ver=${initParam.version}"></script>
<script src="js/login.js?ver=${initParam.version}"></script>
<script src="js/index.js?ver=${initParam.version}"></script>
<script src="js/self.js?ver=${initParam.version}"></script>
<script src="js/query.js?ver=${initParam.version}"></script>
<script src="js/ball.js?ver=${initParam.version}"></script>
<script src="js/corpus.js?ver=${initParam.version}"></script>
<script src="js/agent.js?ver=${initParam.version}"></script>
<script src="js/model.js?ver=${initParam.version}"></script>
<script src="js/vector_settings.js?ver=${initParam.version}"></script>
<script src="js/collapsible.js?ver=${initParam.version}"></script>
</body>

</html>