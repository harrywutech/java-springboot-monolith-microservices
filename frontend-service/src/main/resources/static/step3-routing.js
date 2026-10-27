// Login supplies the course routing marker. This is a demo routing hint, not authentication.
(function () {
  function marker() {var match=document.cookie.match(/(?:^|; )mark=([^;]*)/);return match ? decodeURIComponent(match[1]) : '';}
  if(window.jQuery) jQuery.ajaxPrefilter(function(options,original,xhr) {
    if(/^\/?order(?:\/|$|\?)/.test(options.url)) xhr.setRequestHeader('mark',marker());
  });
  if(window.axios) axios.interceptors.request.use(function(config) {
    if(/^\/?order(?:\/|$|\?)/.test(config.url)) config.headers.mark=marker();return config;
  });
})();
