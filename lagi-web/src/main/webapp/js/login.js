$(document).ready(function () {

    document.getElementById('login-form').addEventListener('keydown', function(event) {
        if (event.key === 'Enter') {
            submitLogin();
        }
    });
    document.getElementById('register-form').addEventListener('keydown', function(event) {
        if (event.key === 'Enter') {
            submitRegister();
        }
    });

    getCategory();
    authLoginCookie(function () {
        getCategory();
    });
    
    
});

document.addEventListener('click', function (event) {
    const clickedInsideUserInfo = event.target.closest('.user-info');
    const clickedInsideDesktopMenu = event.target.closest('#userMenu');
    const clickedInsideMobileMenu = event.target.closest('#userMenu-sm');
    if (clickedInsideUserInfo || clickedInsideDesktopMenu || clickedInsideMobileMenu) {
        return;
    }
    const userMenu = document.getElementById('userMenu');
    const userMenu_sm = document.getElementById('userMenu-sm');
    if (userMenu) {
        userMenu.classList.add('login-hidden');
    }
    if (userMenu_sm) {
        userMenu_sm.classList.add('login-hidden');
    }
});

function setUserMenuVisible(visible) {
    var more = document.getElementById('user-more');
    if (!more) {
        return;
    }
    if (visible) {
        more.classList.remove('login-hidden');
        more.setAttribute('aria-hidden', 'false');
    } else {
        more.classList.add('login-hidden');
        more.setAttribute('aria-hidden', 'true');
    }
}

function toggleUserMenu(e) {
    var ev = e || window.event;
    if (ev && ev.stopPropagation) {
        ev.stopPropagation();
    }
    let auth = getCookie('lagi-auth');
    const userimgStatus = document.querySelector('#user-img span');
    const userImg = document.querySelector('#user-img img');
    if (!auth) {
        openModal(ev);
        return;
    }
    const userMenu = document.getElementById('userMenu');
    const userMenu_sm = document.getElementById('userMenu-sm');
    userImg.src = 'images/avatar.jpg';
    userimgStatus.style.visibility = 'visible';
    userMenu.classList.toggle('login-hidden');
    userMenu_sm.classList.toggle('login-hidden');
}

function openModal(e) {
    document.getElementById('overlay').style.display = 'flex';
    let login_form_jq = $('#login-form');
    let register_form_jq = $('#register-form');
    let window_width = $(window).width();
    let login_width = login_form_jq.outerWidth(true);
    let register_width = register_form_jq.outerWidth(true);
    let max_width = Math.max(login_width, register_width);
    if(e && (window_width * 0.8 > max_width)) {
        const docX = e.pageX;
        const docY = e.pageY;
        let half_window_width = window_width / 2;
        // let window_height = $(window).height();
        let login_letft = docX > half_window_width ? docX - login_width  : docX;
        let login_top = docY - 380 > 0 ? docY - 380 : docY;
        let register_letft = docX > half_window_width ? docX - register_width : docX;
        let register_top = docY - 450 > 0 ? docY - 450 : docY;

        login_form_jq.css('left', login_letft + 'px');
        login_form_jq.css('top', login_top + 'px');
        register_form_jq.css('left', register_letft + 'px');
        register_form_jq.css('top', register_top + 'px');
    } else {
        login_form_jq.css('left', '');
        login_form_jq.css('top','');
        register_form_jq.css('left','');
        register_form_jq.css('top', '');
    }
    showLoginPage();
}

function closeModal() {
    document.getElementById('overlay').style.display = 'none';
    resetForms();
}

function resetForms() {
    document.getElementById('login-username').value = '';
    document.getElementById('login-password').value = '';
    document.getElementById('login-captcha').value = '';
    document.getElementById('register-username').value = '';
    document.getElementById('register-password').value = '';
    document.getElementById('register-confirm-password').value = '';
    document.getElementById('register-captcha').value = '';
    document.getElementById('login-error').style.display = 'none';
    document.getElementById('register-error').style.display = 'none';
}

function showRegisterPage() {
    updateCaptcha(document.querySelector('.register-captcha-image'));
    document.getElementById('login-form').classList.add('login-hidden');
    document.getElementById('register-form').classList.remove('login-hidden');
}

function showLoginPage(e) {
    updateCaptcha(document.querySelector('.login-captcha-image'));
    document.getElementById('register-form').classList.add('login-hidden');
    document.getElementById('login-form').classList.remove('login-hidden');
}

function updateCaptcha(imageElement) {
    let height1 = $('#login-captcha').innerHeight();
    let height2 = $('#register-captcha').innerHeight();
    let codeHeight =  Math.floor(Math.max(height1, height2))
    // let codeHeight = Math.floor($('#login-captcha').innerHeight());
    // if (codeHeight < 0) {
    //     codeHeight = Math.floor($('#register-captcha').innerHeight());
    // }
    imageElement.src = '/user/getCaptcha?height=' + codeHeight + '&width=' + codeHeight * 2 +
        '&fontSize=22&t=' + new Date().getTime();
}

function authLoginCookie(onComplete) {
    const auth = getCookie('lagi-auth');
    if (auth) {
        $.ajax({
            type: "POST",
            contentType: "application/json;charset=utf-8",
            url: "/user/authLoginCookie",
            data: JSON.stringify({"cookieValue": auth}),
            success: function (data) {
                if (data.status === 'success') {
                    localStorage.setItem('userId', data.data.userId);
                    $('#user_box').html(data.data.username);
                    const userimgStatus = document.querySelector('#user-img span');
                    const userImg = document.querySelector('#user-img img');
                    userImg.src = 'images/avatar.jpg';
                    userimgStatus.style.visibility = 'visible';
                    setUserMenuVisible(true);
                    if (typeof window.registerInteractionUserOnLogin === 'function') {
                        window.registerInteractionUserOnLogin();
                    }
                } else {
                    setUserMenuVisible(false);
                }
                if (typeof onComplete === 'function') {
                    onComplete(data);
                }
            },
            error: function (data) {
                if (typeof onComplete === 'function') {
                    onComplete(data);
                }
            }
        });
    } else if (typeof onComplete === 'function') {
        onComplete();
    }
}


function getCategory() {
    window.category = null;
    let categoryTmp = window.category;
    if (categoryTmp === "" || categoryTmp === undefined) {
        currentCategory = "";
    } else {
        currentCategory = categoryTmp;
    }
    let url = "user/getRandomCategory?currentCategory=" + currentCategory;
    const auth = getCookie('lagi-auth');
    if(auth) {
        url +="&userId=" + getCookie('userId');
    }
    $.ajax({
        type: "GET",
        contentType: "application/json;charset=utf-8",
        url: url,
        success: function (res) {
            let category = 'temp';
            if (res.status === 'success') {
                category = res.data.category;
                // setCookie("category", category, 180);
            } else {
                // setCookie("category", category, 1);
            }
            window.category = category;
        },
        error: function (res) {
            let category = 'temp';
            // setCookie("category", category, 1);
            window.category = category;
        }
    });
}


function submitLogin() {
    const errorDiv = document.getElementById('login-error');
    const username = $('#login-username').val();
    const password = $('#login-password').val();
    const captcha = $('#login-captcha').val();
    if (!username || !password) {
        errorDiv.style.display = 'block';
        errorDiv.textContent = '请输入用户名和密码';
        return;
    }
    if (!captcha || captcha.length !== 4) {
        errorDiv.style.display = 'block';
        errorDiv.textContent = '请输入正确的验证码';
        return;
    }
    errorDiv.style.display = 'none';
    errorDiv.textContent = '登录中...';
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "/user/login",
        data: JSON.stringify({"username": username, "password": password, "captcha": captcha}),
        success: function (data) {
            if (data.status === 'success') {
                $('#user_box').html(data.data.username);
                closeModal();
                location.reload(true);

            } else {
                errorDiv.style.display = 'block';
                if (data.msg !== undefined) {
                    errorDiv.textContent = data.msg;
                } else {
                    errorDiv.textContent = '登录失败！';
                }
                updateCaptcha(document.querySelector('.login-captcha-image'));
            }
        },
        error: function (data) {
            errorDiv.style.display = 'block';
            errorDiv.textContent = '登录失败！';
            updateCaptcha(document.querySelector('.login-captcha-image'));
        }
    });
}

function deleteCookie(cname) {
    document.cookie = cname + '=; Max-Age=0; path=/; domain=' + location.hostname;
}

function logout() {
    deleteCookie('lagi-auth');
    deleteCookie('userId');
    localStorage.removeItem('userId');
    window.location.reload();
}

function validateRegisterForm() {
    const username = document.getElementById('register-username').value.trim();
    const password = document.getElementById('register-password').value;
    const confirmPassword = document.getElementById('register-confirm-password').value;
    const captcha = document.getElementById('register-captcha').value.trim();
    const errorMessage = document.getElementById('register-error');

    errorMessage.style.display = 'none';

    const usernameRegex = /^[a-zA-Z0-9_.-]+$/;
    if (username === '') {
        showRegisterError('用户名不能为空');
        return false;
    }
    if (username.length < 3 || username.length > 20) {
        showRegisterError('用户名长度必须在 3 到 20 个字符之间');
        return false;
    }
    if (!usernameRegex.test(username)) {
        showRegisterError('用户名只能包含大小写字母、数字、"."、"-" 和 "_"');
        return false;
    }

    if (password === '') {
        showRegisterError('密码不能为空');
        return false;
    }
    if (password.length < 6) {
        showRegisterError('密码长度必须至少为 6 个字符');
        return false;
    }

    if (confirmPassword !== password) {
        showRegisterError('确认密码与密码不匹配');
        return false;
    }

    if (captcha === '' || captcha.length !== 4) {
        showRegisterError('请输入 4 位验证码');
        return false;
    }
    return true;
}

function showRegisterError(message) {
    const errorMessage = document.getElementById('register-error');
    errorMessage.textContent = message;
    errorMessage.style.display = 'block';
}

function submitRegister() {
    if (!validateRegisterForm()) {
        return;
    }
    const errorDiv = document.getElementById('register-error');
    const registerData = {
        "username": $('#register-username').val(),
        "password": $('#register-password').val(),
        "captcha": $('#register-captcha').val()
    };
    $.ajax({
        type: "POST",
        contentType: "application/json;charset=utf-8",
        url: "/user/register",
        data: JSON.stringify(registerData),
        success: function (data) {
            if (data.status === 'success') {
                closeModal();
                location.reload(true);
            } else {
                errorDiv.style.display = 'block';
                errorDiv.textContent = data.msg;
                updateCaptcha(document.querySelector('.login-captcha-image'));
            }
        },
        error: function (data) {
            errorDiv.style.display = 'block';
            errorDiv.textContent = '注册失败！';
            updateCaptcha(document.querySelector('.login-captcha-image'));
        }
    });
}