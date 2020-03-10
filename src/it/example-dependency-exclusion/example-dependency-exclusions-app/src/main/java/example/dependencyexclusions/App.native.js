setTimeout(function() {
  var app = App.$create__();
  app.m_onModuleLoad__();
  document.write(app.m_message__()); // lgtm[js/eval-like-call]
}, 0);
