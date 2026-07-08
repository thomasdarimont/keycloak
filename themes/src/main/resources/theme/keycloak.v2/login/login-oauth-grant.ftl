<#import "template.ftl" as layout>
<#import "buttons.ftl" as buttons>
<#-- Renders a tree of authorization_details entries recursively (RFC 9396 entries can be deeply nested). -->
<#macro authzDetailEntries entries>
    <ul class="${properties.kcListClass!} kc-authorization-details-entries">
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
            <ul class="${properties.kcListClass!}">
                <#if oauth.clientScopesRequested??>
                    <#list oauth.clientScopesRequested as clientScope>
                        <li>
                            <span><#if !clientScope.parameterizedScopeParameter??>
                                        ${advancedMsg(clientScope.consentScreenText)}
                                    <#else>
                                        ${advancedMsg(clientScope.consentScreenText, clientScope.parameterizedScopeParameter)}
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

            <form class="${properties.kcFormClass} ${properties.kcMarginTopClass!}" action="${url.oauthAction}" method="POST">
                <input type="hidden" name="code" value="${oauth.code}">
                <@buttons.actionGroup horizontal=true>
                    <@buttons.button id="kc-login" name="accept" label="doYes"/>
                    <@buttons.button id="kc-cancel" name="cancel" label="doNo" type="secondary"/>
                </@buttons.actionGroup>
            </form>
        </div>
    </#if>
</@layout.registrationLayout>
