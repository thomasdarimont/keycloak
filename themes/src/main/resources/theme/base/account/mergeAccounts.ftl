<#import "template.ftl" as layout>
<@layout.mainLayout active='mergeAccounts' bodyClass='mergeAccounts'; section>

<div class="row">
  <div class="col-md-10">
    <h2>${msg("mergeAccountsHtmlTitle")}</h2>
  </div>
  <div class="col-md-2 subtitle">
    <span class="subtitle">${msg("allFieldsRequired")}</span>
  </div>
</div>

<form action="${url.mergeAccountsUrl}" class="form-horizontal" method="post">
  <input type="text" readonly value="this is not a login form" style="display: none;">
  <input type="password" readonly value="this is not a login form" style="display: none;">

  <div class="form-group">
    <div class="col-sm-2 col-md-2">
      <label for="username" class="control-label">${msg("username")}</label>
    </div>

    <div class="col-sm-10 col-md-10">
      <input type="text" class="form-control" id="username" name="username" autofocus autocomplete="off">
    </div>
  </div>


  <div class="form-group">
    <div class="col-sm-2 col-md-2">
      <label for="password" class="control-label">${msg("password")}</label>
    </div>

    <div class="col-sm-10 col-md-10">
      <input type="password" class="form-control" id="password" name="password" autofocus autocomplete="off">
    </div>
  </div>

  <div class="form-group">
    <div class="col-sm-2 col-md-2">
      <label for="otp" class="control-label">${msg("otp")}</label>
    </div>

    <div class="col-sm-10 col-md-10">
      <input type="text" class="form-control" id="otp" name="otp" autofocus autocomplete="off">
    </div>
  </div>

  <input type="hidden" id="stateChecker" name="stateChecker" value="${stateChecker?html}">

  <div class="form-group">
    <div id="kc-form-buttons" class="col-md-offset-2 col-md-10 submit">
      <div class="">
        <button type="submit"
                class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}"
                name="submitAction" value="Merge Accounts">${msg("doMergeAccounts")}</button>
      </div>
    </div>
  </div>
</form>

</@layout.mainLayout>