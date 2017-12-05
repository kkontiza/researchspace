/*
 * Copyright (C) 2015-2017, metaphacts GmbH
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, you can receive a copy
 * of the GNU Lesser General Public License from http://www.gnu.org/
 */

package com.metaphacts.rest.endpoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.NamingException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.SimpleAccount;
import org.apache.shiro.authc.credential.PasswordService;
import org.apache.shiro.authz.SimpleRole;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;

import com.fasterxml.jackson.core.JsonParser;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.metaphacts.config.NamespaceRegistry;
import com.metaphacts.data.json.JsonUtil;
import com.metaphacts.repository.RepositoryManager;
import com.metaphacts.rest.feature.CacheControl.NoCache;
import com.metaphacts.security.AnonymousUserFilter;
import com.metaphacts.security.LDAPRealm;
import com.metaphacts.security.MetaphactsSecurityManager;
import com.metaphacts.security.Permissions.ACCOUNTS;
import com.metaphacts.security.Permissions.PERMISSIONS;
import com.metaphacts.security.Permissions.ROLES;
import com.metaphacts.security.SecurityService;
import com.metaphacts.security.ShiroTextRealm;


/**
 * @author Johannes Trame <jt@metaphacts.com>
 */
@Path("security")
@Singleton
public class SecurityEndpoint {

    private static final Logger logger = LogManager.getLogger(SecurityEndpoint.class);

    @Inject
    private PasswordService passwordService;

    @Inject
    private NamespaceRegistry ns;
    
    @Inject
    private RepositoryManager repositoryManager;

    @Inject
    private SecurityService securityService;

    /**
     * POJO to represent the current user for serialization to JSON
     *  //TODO return some more sophisticated user object
     */
    @SuppressWarnings("unused")
    private class CurrentUser{
        public String principal = SecurityUtils.getSubject().getPrincipal().toString();
        public String userURI = ns.getUserIRI().stringValue();
        public boolean isAuthenticated = SecurityUtils.getSubject().isAuthenticated();
        public boolean isAnonymous = SecurityUtils.getSubject().getPrincipal().toString().equals(AnonymousUserFilter.ANONYMOUS_PRINCIPAL);
    }

    @SuppressWarnings("unused")
    private class SessionInfo{
        public long lastAccessTimestamp =  SecurityUtils.getSubject().getSession().getLastAccessTime().getTime();
        public long timout =  SecurityUtils.getSubject().getSession().getTimeout();
        public long idleTime = ( (new Date()).getTime() - lastAccessTimestamp) / 1000;
    }

    @SuppressWarnings("unused")
    private static class Account{
        //empty constructor is need for jackson
        public Account(){}

        public String getPrincipal() {
            return principal;
        }
        public void setPrincipal(String principal) {
            this.principal = principal;
        }
        public String getPassword() {
            return password;
        }
        public void setPassword(String password) {
            this.password = password;
        }
        public String getRoles() {
            return roles;
        }
        public void setRoles(String roles) {
            this.roles = roles;
        }

        public String getPermissions() {
            return permissions;
        }
        public void setPermissions(String permissions) {
            this.permissions = permissions;
        }

        private String principal;
        private String password;
        private String roles;
        private String permissions;

    }

    private static class RoleDefinition{
        //empty constructor is need for jackson
        public RoleDefinition(){}

        @SuppressWarnings("unused")
        public String getRoleName() {
            return roleName;
        }
        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }
        @SuppressWarnings("unused")
        public String getPermissions() {
            return permissions;
        }
        public void setPermissions(String permissions) {
            this.permissions = permissions;
        }

        public String roleName;
        public String permissions;
    }

    @GET()
    @NoCache
    @Path("user")
    @Produces(MediaType.APPLICATION_JSON)
    public CurrentUser getUser() {
        return new CurrentUser();
    }

    @POST()
    @Path("createAccount")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    @RequiresPermissions(ACCOUNTS.CREATE)
    public void createAccount(Account account) {
        try{
           logger.debug("Adding new user account with principal :"+account.getPrincipal());
           String[] roles = StringUtils.isBlank(account.getRoles()) ? new String[]{} : account.getRoles().split(",");
           if(StringUtils.isBlank(account.getPrincipal()))
               throw new IllegalArgumentException("Principal can not be null or empty.");
           if(StringUtils.isBlank(account.getRoles()))
               throw new IllegalArgumentException("Roles can not be null or empty.");
           if(StringUtils.isBlank(account.getPassword()))
               throw new IllegalArgumentException("Password can not be null or empty.");

           if(getShiroTextRealm().accountExists(account.getPrincipal()))
                   throw new IllegalArgumentException("Principal with name "+account.getPrincipal()+" does already exists.");

           getShiroTextRealm().addAccount(account.getPrincipal(), passwordService.encryptPassword(account.getPassword()), roles);
        }catch(IllegalArgumentException e){
            throw new CustomIllegalArgumentException(e.getMessage());
        }
    }

    @GET()
    @NoCache
    @Path("getAllRoleDefinitions")
    @RequiresAuthentication
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresPermissions({ROLES.QUERY, PERMISSIONS.QUERY})
    public List<RoleDefinition> getAllAvailableRoles() {
        List<RoleDefinition> roleDefinitions = Lists.newArrayList();
        for(SimpleRole role :getShiroTextRealm().getRoles().values()){
            RoleDefinition roleDefinition = new RoleDefinition();
            roleDefinition.setRoleName(role.getName());
            roleDefinition.setPermissions(StringUtils.join(role.getPermissions(),','));
            roleDefinitions.add(roleDefinition);
        }
        return roleDefinitions;
    }

    @DELETE()
    @Path("deleteAccount/{userPrincipal}")
    @RequiresAuthentication
    @RequiresPermissions(ACCOUNTS.DELETE)
    public void deleteAccount(@PathParam("userPrincipal") String userPrincipal) {
        try{
            if(StringUtils.isBlank(userPrincipal))
                throw new IllegalArgumentException("User principal can not be null.");

            getShiroTextRealm().deleteAccount(userPrincipal);
        }catch(IllegalArgumentException e){
            throw new CustomIllegalArgumentException(e.getMessage());
        }
    }

    @PUT()
    @Path("updateAccount")
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    @RequiresPermissions(ACCOUNTS.CREATE)
    public void updateAccount(Account account) {
        try{
            logger.debug("Updating user account with principal :"+account.getPrincipal());
            if(StringUtils.isBlank(account.getPrincipal()))
                throw new IllegalArgumentException("Principal can not be null or empty.");
            if(StringUtils.isBlank(account.getRoles()))
                throw new IllegalArgumentException("Roles can not be null or empty.");
            String[] roles = account.getRoles().split(",");

            String password= account.getPassword()!=null ? passwordService.encryptPassword(account.getPassword()) : null;
            getShiroTextRealm().updateAccount(account.getPrincipal(), password, roles);
        }catch(IllegalArgumentException e){
            throw new CustomIllegalArgumentException(e.getMessage());
        }
    }

    @GET()
    @NoCache
    @Path("getAllAccounts")
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    @RequiresPermissions(ACCOUNTS.QUERY)
    public List<Account> getAllAcounts() {
        ArrayList<Account> accounts = Lists.newArrayList();

        ShiroTextRealm r = getShiroTextRealm();
        for(Entry<String, SimpleAccount> user : r.getUsers().entrySet()){
            Account account = new Account();
            account.setPrincipal(user.getKey());
            if(SecurityUtils.getSubject().isPermitted(ROLES.QUERY)){
                Collection<String> roles = user.getValue().getRoles();
                account.setRoles(StringUtils.join(roles,","));
            }

            if(SecurityUtils.getSubject().isPermitted(PERMISSIONS.QUERY)){
                account.setPermissions(StringUtils.join(user.getValue().getObjectPermissions(),","));
            }

            accounts.add(account);
        }

        return accounts;
    }

    @POST
    @NoCache
    @Path("permissions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    public Response hasPermissions(final JsonParser jp) throws IOException {
        final JsonUtil.JsonFieldProducer processor = (jGenerator, input) -> {
            try {
                jGenerator.writeBooleanField(input, SecurityUtils.getSubject().isPermitted(input));
            } catch(IOException e) {
                throw Throwables.propagate(e);
            }
        };
        final StreamingOutput stream = JsonUtil.processJsonMap(jp, processor);
        return Response.ok(stream).build();
    }

    @GET()
    @NoCache
    @Path("getSessionInfo")
    @Produces(MediaType.APPLICATION_JSON)
    public SessionInfo getSession() {
        return new SessionInfo();
    }

    @POST()
    @Path("touchSession")
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    public void touchSession() {
        SecurityUtils.getSubject().getSession().touch();
    }

    @GET()
    @NoCache
    @Path("getLdapUsersMetadata")
    @Produces(MediaType.APPLICATION_JSON)
    @RequiresAuthentication
    @RequiresPermissions(ACCOUNTS.LDAP_SYNC)
    public boolean getLdapUsersMetadata() throws NamingException, Exception {
        MetaphactsSecurityManager securityManager = (MetaphactsSecurityManager) SecurityUtils.getSecurityManager();
        LDAPRealm realm = securityManager.getLDAPRealm();
        String turtle = securityService.renderUsersMetadataToTurtle(realm);
        securityService.saveUsersMetadataTurtleInContainer(turtle, repositoryManager.getAssetRepository());
        return true;
    }

    private ShiroTextRealm getShiroTextRealm(){
        return ((MetaphactsSecurityManager) SecurityUtils.getSecurityManager()).getShiroTextRealm();
    }

    /**
     *  HTTP 500 (Internal Server Error) {@link WebApplicationException} with custom error message
     */
    public class CustomIllegalArgumentException extends WebApplicationException {
        private static final long serialVersionUID = 5775630458408531231L;
        /**
         * @param message the String that is the entity of the 500 response.
         */
        public CustomIllegalArgumentException(String message) {
            super(Response.status(Status.INTERNAL_SERVER_ERROR).
                    entity(message).type("text/plain").build());
        }
    }
}

