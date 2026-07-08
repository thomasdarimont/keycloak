<#import "template.ftl" as layout>
<#-- Renders a tree of authorization_details entries recursively (RFC 9396 entries can be deeply nested). -->
<#macro authzDetailEntries entries>
    <ul class="kc-authorization-details-entries">
        <#list entries as entry>
            <li>
                <#if entry.label?? && entry.value??>
                    <span>${advancedMsg(entry.label)}: <b>${entry.value}</b></span>
                <#elseif entry.label??>
                    <span>${advancedMsg(entry.label)}</span>
                <#elseif entry.value??>
                    <span><b>${entry.value}</b></span>
                </#if>
                <#if entry.fields?has_content><@authzDetailEntries entry.fields/></#if>
            </li>
        </#list>
    </ul>
</#macro>
<#-- Renders the authorization_details associated with a client scope, underneath its consent line. -->
<#macro authorizationDetails details>
    <#if details?has_content>
        <#list details as detail>
            <div class="kc-authorization-details" data-authz-type="${detail.type}">
                <#if detail.title??><span class="kc-authorization-details-title">${advancedMsg(detail.title)}</span></#if>
                <@authzDetailEntries detail.entries/>
            </div>
        </#list>
    </#if>
</#macro>
<@layout.registrationLayout bodyClass="oauth"; section>
    <#if section = "header">
        <#if client.attributes.logoUri??>
            <img src="${client.attributes.logoUri}"/>
        </#if>
        <p>
        <#if client.name?has_content>
            ${msg("oauthGrantTitle",advancedMsg(client.name))}
        <#else>
            ${msg("oauthGrantTitle",client.clientId)}
        </#if>
        </p>
    <#elseif section = "form">
        <div id="kc-oauth" class="content-area">
            <h3>${msg("oauthGrantRequest")}</h3>
            <ul>
                <#if oauth.clientScopesRequested??>
                    <#list oauth.clientScopesRequested as clientScope>
                        <li>
                            <span><#if !clientScope.parameterizedScopeParameter??>
                                        ${advancedMsg(clientScope.consentScreenText)}
                                    <#else>
                                        ${advancedMsg(clientScope.consentScreenText)}: <b>${clientScope.parameterizedScopeParameter}</b>
                                </#if>
                            </span>
                            <@authorizationDetails clientScope.authorizationDetails/>
                        </li>
                    </#list>
                </#if>
            </ul>
            <#if client.attributes.policyUri?? || client.attributes.tosUri??>
                <h3>
                    <#if client.name?has_content>
                        ${msg("oauthGrantInformation",advancedMsg(client.name))}
                    <#else>
                        ${msg("oauthGrantInformation",client.clientId)}
                    </#if>
                    <#if client.attributes.tosUri??>
                        ${msg("oauthGrantReview")}
                        <a href="${client.attributes.tosUri}" target="_blank">${msg("oauthGrantTos")}</a>
                    </#if>
                    <#if client.attributes.policyUri??>
                        ${msg("oauthGrantReview")}
                        <a href="${client.attributes.policyUri}" target="_blank">${msg("oauthGrantPolicy")}</a>
                    </#if>
                </h3>
            </#if>

            <form class="form-actions" action="${url.oauthAction}" method="POST">
                <input type="hidden" name="code" value="${oauth.code}">
                <div class="${properties.kcFormGroupClass!}">
                    <div id="kc-form-options">
                        <div class="${properties.kcFormOptionsWrapperClass!}">
                        </div>
                    </div>

                    <div id="kc-form-buttons">
                        <div class="${properties.kcFormButtonsWrapperClass!}">
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}" name="accept" id="kc-login" type="submit" value="${msg("doYes")}"/>
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonDefaultClass!} ${properties.kcButtonLargeClass!}" name="cancel" id="kc-cancel" type="submit" value="${msg("doNo")}"/>
                        </div>
                    </div>
                </div>
            </form>
            <div class="clearfix"></div>
        </div>
    </#if>
</@layout.registrationLayout>
