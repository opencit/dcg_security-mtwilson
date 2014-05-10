/*
 * Copyright (C) 2013 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.shiro.jdbi;

import com.intel.dcsg.cpg.crypto.Sha1Digest;
import com.intel.dcsg.cpg.crypto.Sha256Digest;
import com.intel.mtwilson.shiro.*;
import com.intel.dcsg.cpg.io.UUID;
import com.intel.dcsg.cpg.x509.X509Util;
import com.intel.mtwilson.shiro.authc.x509.Fingerprint;
import com.intel.mtwilson.shiro.authc.x509.LoginCertificateId;
import com.intel.mtwilson.shiro.authc.x509.X509AuthenticationInfo;
import com.intel.mtwilson.shiro.authc.x509.X509AuthenticationToken;
import com.intel.mtwilson.shiro.jdbi.LoginDAO;
import com.intel.mtwilson.shiro.jdbi.MyJdbi;
import com.intel.mtwilson.user.management.rest.v2.model.Role;
import com.intel.mtwilson.user.management.rest.v2.model.RolePermission;
import com.intel.mtwilson.user.management.rest.v2.model.UserLoginCertificate;
import com.intel.mtwilson.user.management.rest.v2.model.User;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;

/**
 *
 * @author jbuhacoff
 */
public class JdbcCertificateRealm extends AuthorizingRealm {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JdbcCertificateRealm.class);
    
    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof X509AuthenticationToken;
    }
    
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection pc) {
        if (pc == null) {
            throw new AuthorizationException("Principal must be provided");
        }
        SimpleAuthorizationInfo authzInfo = new SimpleAuthorizationInfo();
        for (String realmName : pc.getRealmNames()) {
            log.debug("doGetAuthorizationInfo for realm: {}", realmName);
        }
        Collection<Username> usernames = pc.byType(Username.class);
        for (Username username : usernames) {
            log.debug("doGetAuthorizationInfo for username: {}", username.getUsername());
        }
        try (LoginDAO dao = MyJdbi.authz()) {
            
            Collection<LoginCertificateId> loginCertificateIds = pc.byType(LoginCertificateId.class);
            for (LoginCertificateId loginCertificateId : loginCertificateIds) {
                log.debug("doGetAuthorizationInfo for login certificate id: {}", loginCertificateId.getLoginCertificateId());
                
                
                List<Role> roles = dao.findRolesByUserLoginCertificateId(loginCertificateId.getLoginCertificateId());
                HashSet<String> roleIds = new HashSet<>();
                for (Role role : roles) {
                    log.debug("doGetAuthorizationInfo found role: {}", role.getRoleName());
                    roleIds.add(role.getId().toString());
                    authzInfo.addRole(role.getRoleName());
                }
                if (!roleIds.isEmpty()) {
                    List<RolePermission> permissions = dao.findRolePermissionsByCertificateRoleIds(roleIds);
                    for (RolePermission permission : permissions) {
                        log.debug("doGetAuthorizationInfo found permission: {} {} {}", permission.getPermitDomain(), permission.getPermitAction(), permission.getPermitSelection());
                        authzInfo.addStringPermission(String.format("%s:%s:%s", permission.getPermitDomain(), permission.getPermitAction(), permission.getPermitSelection()));
                    }
                }
                
            }
        } catch (Exception e) {
            log.debug("doGetAuthorizationInfo error", e);
            throw new AuthenticationException("Internal server error", e); // TODO: i18n
        }

        return authzInfo;
    }
    
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        X509AuthenticationToken xToken = (X509AuthenticationToken) token;
        UserLoginCertificate userLoginCertificate = null;
        User user = null;
        if( xToken.getPrincipal() instanceof Fingerprint ) {
            Fingerprint fingerprint = (Fingerprint)xToken.getPrincipal();
            log.debug("doGetAuthenticationInfo for fingerprint {}", fingerprint.getHex());
            try (LoginDAO dao = MyJdbi.authz()) {
                if( Sha256Digest.isValid(fingerprint.getBytes())) {
                    userLoginCertificate = dao.findUserLoginCertificateBySha256(fingerprint.getBytes()); 
                }
                else if( Sha1Digest.isValid(fingerprint.getBytes())) {
                    userLoginCertificate = dao.findUserLoginCertificateBySha1(fingerprint.getBytes()); 
                }
                else {
                    log.error("Unsupported digest length {}", fingerprint.getBytes().length);
                }
                if(userLoginCertificate != null && userLoginCertificate.isEnabled() ) {
                    user = dao.findUserByIdEnabled(userLoginCertificate.getUserId(),true);
                }
    //            xToken.
    //            userLoginCertificate = dao.findUserLoginCertificateByUsername(username);
            } catch (Exception e) {
                log.debug("doGetAuthenticationInfo error", e);
                throw new AuthenticationException("Internal server error", e); // TODO: i18n
            }
        }
        if (userLoginCertificate == null || user == null) {
            return null;
        }
        
        
        log.debug("doGetAuthenticationInfo found user login certificate id {}", userLoginCertificate.getId());
        SimplePrincipalCollection principals = new SimplePrincipalCollection();
        principals.add(new UserId(userLoginCertificate.getUserId()), getName());
        principals.add(new Username(user.getUsername()), getName());
        principals.add(new LoginCertificateId(userLoginCertificate.getUserId(), userLoginCertificate.getId()), getName());
        // should we add the Fingerprint principal?  or is it enough to use LoginCertificateId ?
        X509AuthenticationInfo info = new X509AuthenticationInfo();
        info.setPrincipals(principals);
        try {
            info.setCredentials(X509Util.decodeDerCertificate(userLoginCertificate.getCertificate()));
        }
        catch(CertificateException e) {
            throw new AuthenticationException("Invalid certificate", e); // TODO: i18n
        }

        return info;
    }
}
