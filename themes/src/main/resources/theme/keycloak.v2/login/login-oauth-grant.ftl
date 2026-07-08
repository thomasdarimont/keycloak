<#import "template.ftl" as layout>
<#import "buttons.ftl" as buttons>
<#-- Resolves a message key, falling back to the given text when no translation exists.
     advancedMsg("${key}") returns the message, or the key itself when missing. -->
<#function msgOrDefault key fallback>
    <#local resolved = advancedMsg("$" + "{" + key + "}")>
    <#if resolved == key><#return fallback><#else><#return resolved></#if>
</#function>
<#-- Renders a tree of authorization_details entries recursively (RFC 9396 entries can be deeply nested).
     Labels are resolved by convention from the entry type: <type>_entry_<name>, falling back to the raw name.
     An optional help tooltip is shown when a <type>_entry_<name>_help message is defined. -->
<#macro authzDetailEntries type entries>
    <ul class="${properties.kcListClass!} kc-authorization-details-entries">
        <#list entries as entry>
            <li>
                <#if entry.name??>
                    <#local help = msgOrDefault(type + "_entry_" + entry.name + "_help", "")>
                    <span>${msgOrDefault(type + "_entry_" + entry.name, entry.name)}<#if entry.value??>: <b>${entry.value}</b></#if><#if help?has_content> <span class="kc-authorization-details-help" tabindex="0" title="${help}" aria-label="${help}">&#9432;</span></#if></span>
                <#elseif entry.value??>
                    <span><b>${entry.value}</b></span>
                </#if>
                <#if entry.description?has_content><div class="kc-authorization-details-description">${advancedMsg(entry.description)}</div></#if>
                <#if entry.fields?has_content><@authzDetailEntries type entry.fields/></#if>
            </li>
        </#list>
    </ul>
</#macro>
<#-- Renders the authorization_details associated with a client scope, underneath its consent line.
     The heading is resolved from <type>_title and only shown when such a message exists. -->
<#macro authorizationDetails details>
    <#if details?has_content>
        <#list details as detail>
            <div class="kc-authorization-details" data-authz-type="${detail.type}">
                <#local title = msgOrDefault(detail.type + "_title", "")>
                <#if title?has_content><span class="kc-authorization-details-title">${title}</span></#if>
                <@authzDetailEntries detail.type detail.entries/>
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
