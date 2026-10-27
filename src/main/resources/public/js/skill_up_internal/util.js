document.addEventListener('visibilitychange', account_status, false);
account_status();


function serializeForm(formData){
    var obj = {};
    for (var pair of formData.entries()) {
        obj[pair[0]] = pair[1];
    }
    return obj;
}

function setCookie(name,value)
{
    document.cookie = name + "=" + encodeURIComponent(value) + ";max-age=3600;path=/;SameSite=Lax";
}

//Read cookies
function getCookie(name)
{
    var arr,reg=new RegExp("(^| )"+name+"=([^;]*)(;|$)");

    if(arr=document.cookie.match(reg))

        return unescape(arr[2]);
    else
        return null;
}

//Delete cookies
function delCookie(name)
{
    var exp = new Date();
    exp.setTime(exp.getTime() - 1);
    var cval=getCookie(name);
    if(cval!=null)
        document.cookie= name + "="+cval+";expires="+exp.toGMTString();
}

function getSystemTime() {
    var time;
    $.ajax({
        type: 'GET',
        url: '/system/time',
        data: {},
        async: false,
        headers: {
          'Content-Type': 'application/json;charset=UTF-8'
        },
        success: function (data) {
          time = data;
          console.log("data: " + time);
        },
        error: function (data) {
          console.log(data);
          alert("error: " + data.responseText);
        }
    });
    return time;
}

function account_status()
{
    console.log("check account status");
    var name = getCookie("user_name");
    if (name !== null) {
        document.getElementById("account_action").textContent="Welcome, " + name;
    } else {
        document.getElementById("account_action").textContent="please  login";
    }
}

function serializeForm(formData){
    var obj = {};
    for (var pair of formData.entries()) {
      obj[pair[0]] = pair[1];
    }
    return obj;
}

function GetRequest() {
    var url = location.search; //Get the query string after the question mark in the URL
    var theRequest = new Object();
    if (url.indexOf("?") != -1) {
        var str = url.substr(1);
        strs = str.split("&");
        for(var i = 0; i < strs.length; i ++) {
            theRequest[strs[i].split("=")[0]]=unescape(strs[i].split("=")[1]);
        }
    }
    return theRequest;
}
