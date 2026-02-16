/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.security.user.core.authorization;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.core.util.StringUtils;
import org.wso2.micro.integrator.security.user.core.UserCoreConstants;
import org.wso2.micro.integrator.security.user.core.common.GhostResource;
import org.wso2.micro.integrator.security.user.core.UserStoreException;
import org.wso2.micro.integrator.security.user.core.UserStoreManager;
import org.wso2.micro.integrator.security.user.core.internal.UserStoreMgtDSComponent;
import org.wso2.micro.integrator.security.user.core.util.DatabaseUtil;
import org.wso2.micro.integrator.security.user.core.util.UserCoreUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.sql.DataSource;

import static org.wso2.micro.integrator.security.SecurityConstants.CASE_INSENSITIVE_USERNAME;

public class PermissionTree {

    private static final String PERMISSION_CACHE_MANAGER = "PERMISSION_CACHE_MANAGER";
    private static final String PERMISSION_CACHE = "PERMISSION_CACHE";
    private static Log log = LogFactory.getLog(PermissionTree.class);
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock read = readWriteLock.readLock();
    private final Lock write = readWriteLock.writeLock();
    protected TreeNode root;
    protected int tenantId;
    protected String cacheIdentifier;
    protected volatile int hashValueOfRootNode;
    protected DataSource dataSource;
    protected boolean preserveCaseForResources = true;

    /**
     * On the server startup, all permissions are populated from the DB and the
     * permission tree is built in memory..
     *
     * @throws UserStoreException - SQL exceptions
     */

    public PermissionTree(String cacheIdentifier, int tenantId, DataSource dataSource) {
        root = new TreeNode("/");
        this.cacheIdentifier = cacheIdentifier;
        this.tenantId = tenantId;
        this.dataSource = dataSource;
    }

    /**
     * On the server startup, all permissions are populated from the DB and the
     * permission tree is built in memory..
     * With preserveCaseForResources it will support for both case sensitive and insensitive resources.
     *
     * @throws UserStoreException - SQL exceptions
     */
    public PermissionTree(String cacheIdentifier, int tenantId, DataSource dataSource, boolean preserveCaseForResources) {
        root = new TreeNode("/");
        this.cacheIdentifier = cacheIdentifier;
        this.tenantId = tenantId;
        this.dataSource = dataSource;
        this.preserveCaseForResources = preserveCaseForResources;
    }

    /**
     * This private constructor will be used by the database loading code to
     * create a new copy of the permission node.
     */
    private PermissionTree() {
        root = new TreeNode("/");
    }

    /**
     * Getting existing cache if the cache available, else returns a newly created cache.
     * This logic handles by javax.cache implementation
     */
    private Cache<PermissionTreeCacheKey, GhostResource<TreeNode>> getPermissionTreeCache() {
        Cache<PermissionTreeCacheKey, GhostResource<TreeNode>> cache = null;
        CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager(PERMISSION_CACHE_MANAGER);
        cache = cacheManager.getCache(PERMISSION_CACHE);
        return cache;
    }

    void authorizeUserInTree(String userName, String resourceId, String action, boolean updateCache) throws UserStoreException {
        if (!isCaseSensitiveUsername(userName, tenantId)) {
            userName = userName.toLowerCase();
        }
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() != null) {
                List<String> paths = sr.getUnprocessedPaths();
                TreeNode tn = sr.getLastNode().create(paths);
                tn.authorizeUser(userName, PermissionTreeUtil.actionToPermission(action));

            } else {
                sr.getLastNode().authorizeUser(userName,
                        PermissionTreeUtil.actionToPermission(action));
            }
            if (updateCache) {
                invalidateCache(root);
            }
        } catch (IllegalArgumentException e) {
            throw new UserStoreException("Error while authorizing user: " + userName +
                    "in permission tree for resource id: " + resourceId + "for action: " + action, e);
        } finally {
            write.unlock();
        }
    }

    void denyUserInTree(String userName, String resourceId, String action, boolean updateCache) throws UserStoreException {
        if (!isCaseSensitiveUsername(userName, tenantId)) {
            userName = userName.toLowerCase();
        }
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() != null) {
                List<String> paths = sr.getUnprocessedPaths();
                TreeNode tn = sr.getLastNode().create(paths);
                tn.denyUser(userName, PermissionTreeUtil.actionToPermission(action));
            } else {
                sr.getLastNode().denyUser(userName, PermissionTreeUtil.actionToPermission(action));
            }
            if (updateCache) {
                invalidateCache(root);
            }
        } catch (IllegalArgumentException e) {
            throw new UserStoreException("Error while denying user: " + userName +
                    "in permission tree for resource id: " + resourceId + "for action: " + action, e);
        } finally {
            write.unlock();
        }
    }

    void authorizeRoleInTree(String roleName, String resourceId, String action, boolean updateCache) throws UserStoreException {
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() != null) {
                List<String> paths = sr.getUnprocessedPaths();
                TreeNode tn = sr.getLastNode().create(paths);
                tn.authorizeRole(roleName, PermissionTreeUtil.actionToPermission(action));

            } else {
                sr.getLastNode().authorizeRole(roleName,
                        PermissionTreeUtil.actionToPermission(action));
            }
            if (updateCache) {
                invalidateCache(root);
            }
        } catch (IllegalArgumentException e) {
            throw new UserStoreException("Error while authorizing role: " + roleName +
                    "in permission tree for resource id: " + resourceId + "for action: " + action, e);
        } finally {
            write.unlock();
        }
    }

    void denyRoleInTree(String roleName, String resourceId, String action, boolean updateCache) throws UserStoreException {
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() != null) {
                List<String> paths = sr.getUnprocessedPaths();
                TreeNode tn = sr.getLastNode().create(paths);
                tn.denyRole(roleName, PermissionTreeUtil.actionToPermission(action));

            } else {
                sr.getLastNode().denyRole(roleName, PermissionTreeUtil.actionToPermission(action));
            }
            if (updateCache) {
                invalidateCache(root);
            }
        } catch (IllegalArgumentException e) {
            throw new UserStoreException("Error while denying role: " + roleName +
                    "in permission tree for resource id: " + resourceId + "for action: " + action, e);
        } finally {
            write.unlock();
        }
    }

    /**
     * Find permission for given 'role' starting from the TreeNode 'node' and
     * using the path segments given in the List as pathParts
     *
     * @param role       the role for which the permissions are searched
     * @param permission the permission checked
     * @param sr         the SearchResult that merges the permissions as the tree is
     *                   traversed
     * @param node       the current node
     * @param pathParts  the list of path segments to traverse
     * @return the final SearchResult that merges the permissions
     */
    SearchResult getRolePermission(String role, TreeNode.Permission permission, SearchResult sr,
                                   TreeNode node, List<String> pathParts) {
        read.lock();
        try {
            if (node == null) {
                node = root;
            }

            if (sr == null) {
                sr = new SearchResult();
            }

            Boolean currentNodeAllows = node.isRoleAuthorized(role, permission);
            if (currentNodeAllows == Boolean.TRUE) {
                sr.setLastNodeAllowedAccess(Boolean.TRUE);
            } else if (currentNodeAllows == Boolean.FALSE) {
                sr.setLastNodeAllowedAccess(Boolean.FALSE);
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;

            } else {

                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getRolePermission(role, permission, sr, child, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Find permission for given 'user' starting from the TreeNode 'node' and
     * using the path segments given in the List as pathParts
     *
     * @param user       the user for which the permissions are searched
     * @param permission the permission checked
     * @param sr         the SearchResult that merges the permissions as the tree is
     *                   traversed
     * @param node       the current node
     * @param pathParts  the list of path segments to traverse
     * @return the final SearchResult that merges the permissions
     */
    SearchResult getUserPermission(String user, TreeNode.Permission permission, SearchResult sr,
                                   TreeNode node, List<String> pathParts) {
        if (!isCaseSensitiveUsername(user, tenantId)) {
            user = user.toLowerCase();
        }
        read.lock();
        try {
            if (node == null) {
                node = root;
            }
            if (sr == null) {
                sr = new SearchResult();
            }

            Boolean currentNodeAllows = node.isUserAuthorized(user, permission);
            if (currentNodeAllows == Boolean.TRUE) {
                sr.setLastNodeAllowedAccess(Boolean.TRUE);
            } else if (currentNodeAllows == Boolean.FALSE) {
                sr.setLastNodeAllowedAccess(Boolean.FALSE);
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;

            } else {

                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getUserPermission(user, permission, sr, child, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Find the allowed Users for a given resource by traversing the whole
     * pemission tree.
     *
     * @param sr         - search result to contain allowed users
     * @param node       - current node
     * @param permission - permission
     * @param pathParts  - list of path segments to traverse
     * @return - search result with allowed users
     */

    SearchResult getAllowedUsersForResource(SearchResult sr, TreeNode node,
                                            TreeNode.Permission permission, List<String> pathParts) {
        read.lock();
        try {
            if (node == null) {
                node = root;
            }
            if (sr == null) {
                sr = new SearchResult();
            }

            /**
             * Add allowed users of the current node to our list in the sr
             */
            Map<String, BitSet> allowUsers = node.getUserAllowPermissions();
            for (Map.Entry<String, BitSet> entry : allowUsers.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())) {
                    if (!sr.getAllowedEntities().contains(entry.getKey())) {
                        sr.getAllowedEntities().add(entry.getKey());
                    }
                }
            }

            /**
             * Remove denied users of the current node from our list in the sr
             */
            Map<String, BitSet> denyUsers = node.getUserDenyPermissions();
            for (Map.Entry<String, BitSet> entry : denyUsers.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())
                        && sr.getAllowedEntities().contains(entry.getKey())) {
                    sr.getAllowedEntities().remove(entry.getKey());
                }
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;
            } else {
                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getAllowedUsersForResource(sr, child, permission, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Find the allowed Roles for a given resource by traversing the whole
     * pemission tree.
     *
     * @param sr         - search result to contain allowed roles
     * @param node       - current node
     * @param permission - permission
     * @param pathParts  - list of path segments to traverse
     * @return - search result with allowed roles
     */

    SearchResult getAllowedRolesForResource(SearchResult sr, TreeNode node,
                                            TreeNode.Permission permission, List<String> pathParts) {
        read.lock();
        try {
            if (node == null) {
                node = root;
            }

            if (sr == null) {
                sr = new SearchResult();
            }

            /**
             * Add allowed roles of the current node to our list in the sr
             */
            Map<String, BitSet> allowRoles = node.getRoleAllowPermissions();
            for (Map.Entry<String, BitSet> entry : allowRoles.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())) {
                    if (!sr.getAllowedEntities().contains(entry.getKey())) {
                        sr.getAllowedEntities().add(entry.getKey());
                    }
                }
            }

            /**
             * Remove denied roles of the current node from our list in the sr
             */
            Map<String, BitSet> denyRoles = node.getRoleDenyPermissions();
            for (Map.Entry<String, BitSet> entry : denyRoles.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())
                        && sr.getAllowedEntities().contains(entry.getKey())) {
                    sr.getAllowedEntities().remove(entry.getKey());
                }
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;
            } else {
                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getAllowedRolesForResource(sr, child, permission, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Find the denied Roles for a given resource by traversing the whole
     * pemission tree.
     *
     * @param sr         - search result to contain denied roles
     * @param node       - current node
     * @param permission - permission
     * @param pathParts  - list of path segments to traverse
     * @return - search result with denied roles
     */
    SearchResult getDeniedRolesForResource(SearchResult sr, TreeNode node,
                                           TreeNode.Permission permission, List<String> pathParts) {

        read.lock();
        try {
            if (sr == null) {
                sr = new SearchResult();
            }

            if (node == null) {
                node = root;
            }

            /**
             * Add denied roles of the current node to our list in the sr
             */
            Map<String, BitSet> denyRoles = node.getRoleDenyPermissions();
            for (Map.Entry<String, BitSet> entry : denyRoles.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())) {
                    if (!sr.getDeniedEntities().contains(entry.getKey())) {
                        sr.getDeniedEntities().add(entry.getKey());
                    }
                }
            }

            /**
             * Remove allowed roles of the current node from our list in the sr
             */
            Map<String, BitSet> allowRoles = node.getRoleAllowPermissions();
            for (Map.Entry<String, BitSet> entry : allowRoles.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal()) && sr.getDeniedEntities().contains(entry.getKey())) {
                    sr.getDeniedEntities().remove(entry.getKey());
                }
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;
            } else {
                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getDeniedRolesForResource(sr, child, permission, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    /**
     * Find the denied users for a given resource by traversing the whole
     * pemission tree.
     *
     * @param sr         - search result to contain denied users
     * @param node       - current node
     * @param permission - permission
     * @param pathParts  - list of path segments to traverse
     * @return - search result with denied users
     */
    SearchResult getDeniedUsersForResource(SearchResult sr, TreeNode node,
                                           TreeNode.Permission permission, List<String> pathParts) {
        read.lock();
        try {
            if (sr == null) {
                sr = new SearchResult();
            }

            if (node == null) {
                node = root;
            }

            /**
             * Add denied users of the current node to our list in the sr
             */
            Map<String, BitSet> denyUsers = node.getUserDenyPermissions();
            for (Map.Entry<String, BitSet> entry : denyUsers.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal())) {
                    if (!sr.getDeniedEntities().contains(entry.getKey())) {
                        sr.getDeniedEntities().add(entry.getKey());
                    }
                }
            }

            /**
             * Remove allowed users of the current node from our list in the sr
             */
            Map<String, BitSet> allowUsers = node.getUserAllowPermissions();
            for (Map.Entry<String, BitSet> entry : allowUsers.entrySet()) {
                BitSet bs = entry.getValue();
                if (bs.get(permission.ordinal()) && sr.getDeniedEntities().contains(entry.getKey())) {
                    sr.getDeniedEntities().remove(entry.getKey());
                }
            }

            if (pathParts == null || pathParts.isEmpty()) {
                sr.setLastNode(node);
                sr.setUnprocessedPaths(null);
                return sr;
            } else {
                String key = pathParts.get(0);
                if (key != null && key.length() > 0) {
                    TreeNode child = node.getChild(key);
                    if (child != null) {
                        pathParts.remove(0);
                        return getDeniedUsersForResource(sr, child, permission, pathParts);
                    }
                }
                sr.setLastNode(node);
                return sr;
            }
        } finally {
            read.unlock();
        }
    }

    void clearRoleAuthorization(String roleName, String action) throws UserStoreException {
        TreeNode.Permission permission = PermissionTreeUtil.actionToPermission(action);
        clearRoleAuthorization(roleName, root, permission);
        invalidateCache(root);
    }

    void updateRoleNameInCache(String roleName, String newRoleName) throws UserStoreException {
        updateRoleNameInCache(roleName, newRoleName, root);
        invalidateCache(root);
    }

    void clearRoleAuthorization(String roleName) throws UserStoreException {
        clearRoleAuthorization(roleName, root);
        invalidateCache(root);
    }

    void clearRoleAuthorization(String roleName, String resourceId, String action) throws UserStoreException {
        SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
        write.lock();
        try {
            if (sr.getUnprocessedPaths() == null) {
                TreeNode.Permission permission = PermissionTreeUtil.actionToPermission(action);

                Map<String, BitSet> allowRoles = sr.getLastNode().getRoleAllowPermissions();
                BitSet bs = allowRoles.get(roleName);
                if (bs == null) {
                    bs = allowRoles.get(modify(roleName));
                }
                if (bs != null) {
                    bs.clear(permission.ordinal());
                }

                Map<String, BitSet> denyRoles = sr.getLastNode().getRoleDenyPermissions();
                bs = denyRoles.get(roleName);
                if (bs != null) {
                    bs.clear(permission.ordinal());
                }
            }
            invalidateCache(root);
        } finally {
            write.unlock();
        }
    }

    void clearUserAuthorization(String userName) throws UserStoreException {
        clearUserAuthorization(userName, root);
        invalidateCache(root);
    }

    void clearUserAuthorization(String userName, String resourceId, String action) throws UserStoreException {
        if (!isCaseSensitiveUsername(userName, tenantId)) {
            userName = userName.toLowerCase();
        }
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() == null || sr.getUnprocessedPaths().isEmpty()) {
                TreeNode.Permission permission = PermissionTreeUtil.actionToPermission(action);

                Map<String, BitSet> allowUsers = sr.getLastNode().getUserAllowPermissions();
                BitSet bs = allowUsers.get(userName);
                if (bs != null) {
                    bs.clear(permission.ordinal());
                }

                Map<String, BitSet> denyUsers = sr.getLastNode().getUserDenyPermissions();
                bs = denyUsers.get(userName);
                if (bs != null) {
                    bs.clear(permission.ordinal());
                }
            }
            invalidateCache(root);
        } finally {
            write.unlock();
        }
    }

    /**
     * This method is only used for UI permissions
     *
     * @param roles     roles that needs to get resources
     * @param resources resource list
     * @param path      list of path segments to traverse
     * @throws UserStoreException throws
     */

    void getUIResourcesForRoles(String[] roles, List<String> resources, String path) throws UserStoreException {
        List<String> paths = PermissionTreeUtil.toComponenets(path);
        TreeNode node = null;
        read.lock();
        try {
            node = root;
            for (String name : paths) {
                node = node.getChild(name);
                if (node == null) {
                    break;
                }
            }
        } finally {
            read.unlock();
        }

        if (node == null) {
            throw new UserStoreException("Invalid Permission root path provided");
        }

        TreeNode permissionNode = root.getChild(UserCoreConstants.UI_PERMISSION_NAME);

        if (permissionNode == null) {
            throw new UserStoreException("Invalid Permission root path provided");
        }

        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        getUIResourcesForRoles(roles,
                resources,
                "", /* The old code has this. So use the same thing*/
                PermissionTreeUtil.actionToPermission(UserCoreConstants.UI_PERMISSION_ACTION),
                permissionNode);
    }

    void getUIResourcesForRoles(String[] roles, List<String> resources, String path,
                                TreeNode.Permission permission, TreeNode node) {
        read.lock();
        try {
            String currentPath = path + "/" + node.getName();

            Map<String, BitSet> bsAllowed = node.getRoleAllowPermissions();
            for (String role : roles) {
                BitSet bs = bsAllowed.get(role);
                if (bs == null) {
                    bs = bsAllowed.get(modify(role));
                }
                if (bs != null && bs.get(permission.ordinal())) {
                    resources.add(currentPath);
                    break;
                }
            }

            Map<String, TreeNode> children = node.getChildren();
            for (TreeNode treeNode : children.values()) {
                if (treeNode != null) {
                    getUIResourcesForRoles(roles, resources, currentPath, permission, treeNode);
                }
            }
        } finally {
            read.unlock();
        }
    }


    void clearResourceAuthorizations(String resourceId) throws UserStoreException {
        write.lock();
        try {
            SearchResult sr = getNode(root, PermissionTreeUtil.toComponenets(resourceId));
            if (sr.getUnprocessedPaths() == null) {
                sr.getLastNode().getUserAllowPermissions().clear();
                sr.getLastNode().getUserDenyPermissions().clear();
                sr.getLastNode().getRoleAllowPermissions().clear();
                sr.getLastNode().getRoleDenyPermissions().clear();
            }
            invalidateCache(root);
        } finally {
            write.unlock();
        }
    }

//////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Clear all role authorization based on action starting from the given node
     *
     * @param node the start node to begin the search
     * @param
     * @return the result as a SearchResultpathParts a List of
     * @see SearchResult
     */
    private void clearRoleAuthorization(String roleName, TreeNode node, TreeNode.Permission permission) {
        write.lock();
        try {
            Map<String, BitSet> allowRoles = node.getRoleAllowPermissions();
            Map<String, BitSet> denyRoles = node.getRoleDenyPermissions();

            BitSet bs = allowRoles.get(roleName);
            if (bs == null) {
                bs = allowRoles.get(modify(roleName));
            }
            if (bs != null) {
                bs.clear(permission.ordinal());
            }

            bs = denyRoles.get(roleName);
            if (bs != null) {
                bs.clear(permission.ordinal());
            }

            Map<String, TreeNode> childMap = node.getChildren();
            if (childMap != null && childMap.size() > 0) {
                for (TreeNode treeNode : childMap.values()) {
                    clearRoleAuthorization(roleName, treeNode, permission);
                }
            }
            //     invalidateCache(root);
        } finally {
            write.unlock();
        }
    }

    /**
     * Clear all role authorization based on action starting from the given node
     *
     * @param node the start node to begin the search
     * @param
     * @return the result as a SearchResultpathParts a List of
     * @see SearchResult
     */
    private void clearRoleAuthorization(String roleName, TreeNode node) {
        write.lock();
        try {
            Map<String, BitSet> allowRoles = node.getRoleAllowPermissions();
            Map<String, BitSet> denyRoles = node.getRoleDenyPermissions();

            BitSet bs = allowRoles.get(roleName);
            boolean modified = false;
            if (bs == null) {
                bs = allowRoles.get(modify(roleName));
                modified = true;
            }
            if (bs != null) {
                if (modified) {
                    allowRoles.remove(modify(roleName));
                } else {
                    allowRoles.remove(roleName);
                }
            }
            modified = false;
            bs = denyRoles.get(roleName);
            if (bs == null) {
                bs = denyRoles.get(modify(roleName));
                modified = true;
            }
            if (bs != null) {
                if (modified) {
                    denyRoles.remove(modify(roleName));
                } else {
                    denyRoles.remove(roleName);
                }
            }

            Map<String, TreeNode> childMap = node.getChildren();
            if (childMap != null && childMap.size() > 0) {
                for (TreeNode treeNode : childMap.values()) {
                    clearRoleAuthorization(roleName, treeNode);
                }
            }
            // invalidateCache(root);
        } finally {
            write.unlock();
        }
    }

    private void updateRoleNameInCache(String roleName, String newRoleName, TreeNode node) {
        Map<String, BitSet> allowRoles = node.getRoleAllowPermissions();
        Map<String, BitSet> denyRoles = node.getRoleDenyPermissions();
        write.lock();
        try {
            boolean modified = false;
            BitSet bs = allowRoles.get(roleName);
            if (bs == null) {
                bs = allowRoles.get(modify(roleName));
                modified = true;
            }
            if (bs != null) {
                if (!modified) {
                    allowRoles.remove(roleName);
                } else {
                    allowRoles.remove(modify(roleName));
                }
                allowRoles.put(modify(newRoleName), bs);
            }

            modified = false;
            bs = denyRoles.get(roleName);
            if (bs == null) {
                bs = denyRoles.get(modify(roleName));
                modified = true;
            }
            if (bs != null) {
                if (!modified) {
                    denyRoles.remove(roleName);
                } else {
                    denyRoles.remove(modify(roleName));
                }
                denyRoles.put(modify(newRoleName), bs);
            }

            Map<String, TreeNode> childMap = node.getChildren();
            if (childMap != null && childMap.size() > 0) {
                for (TreeNode treeNode : childMap.values()) {
                    updateRoleNameInCache(roleName, newRoleName, treeNode);
                }
            }
            //      invalidateCache(root);
        } finally {
            write.unlock();
        }
    }


    private void clearUserAuthorization(String userName, TreeNode node) {
        if (!isCaseSensitiveUsername(userName, tenantId)) {
            userName = userName.toLowerCase();
        }
        write.lock();
        try {
            Map<String, BitSet> allowUsers = node.getUserAllowPermissions();
            Map<String, BitSet> denyUsers = node.getUserDenyPermissions();

            BitSet bs = allowUsers.get(userName);
            if (bs != null) {
                allowUsers.remove(userName);
            }

            bs = denyUsers.get(userName);
            if (bs != null) {
                denyUsers.remove(userName);
            }

            Map<String, TreeNode> childMap = node.getChildren();
            if (childMap != null && childMap.size() > 0) {
                for (TreeNode treeNode : childMap.values()) {
                    clearUserAuthorization(userName, treeNode);
                }
            }
            //invalidateCache(root);
        } finally {
            write.unlock();
        }
    }


    /**
     * Clears all permission information in current node.
     */
    void clear() {
        Cache<PermissionTreeCacheKey, GhostResource<TreeNode>> permissionCache = this.getPermissionTreeCache();
        if (permissionCache != null) {
            write.lock();
            try {
                this.root.clearNodes();
                this.hashValueOfRootNode = -1;
                PermissionTreeCacheKey cacheKey = new PermissionTreeCacheKey(cacheIdentifier, tenantId);
                // TODO Is this clear all?
                permissionCache.remove(cacheKey);
            } finally {
                write.unlock();
            }
        }

    }

    /**
     * update permission tree from cache
     *
     * @throws org.wso2.micro.integrator.security.user.core.UserStoreException throws if fail to update permission tree from DB
     */
    void updatePermissionTree() throws UserStoreException {
        updatePermissionTree("");
    }

    /**
     * Update permission tree from database for a given resource id if permission tree is already cached.
     * If permission tree isn't cached, then this method will load full permission tree.
     *
     * @param resourceId registry resource path
     * @throws org.wso2.micro.integrator.security.user.core.UserStoreException throws if fail to update permission tree from DB
     */
    void updatePermissionTree(String resourceId) throws UserStoreException {
        Cache<PermissionTreeCacheKey, GhostResource<TreeNode>> permissionCache = this.getPermissionTreeCache();
        if (permissionCache != null) {
            PermissionTreeCacheKey cacheKey = new PermissionTreeCacheKey(cacheIdentifier, tenantId);
            GhostResource<TreeNode> cacheEntry = (GhostResource<TreeNode>) permissionCache.get(cacheKey);
            if (permissionCache.containsKey(cacheKey) && cacheEntry != null) {
                if (cacheEntry.getResource() == null) {
                    synchronized (this) {
                        cacheEntry = (GhostResource<TreeNode>) permissionCache.get(cacheKey);
                        if (cacheEntry == null || cacheEntry.getResource() == null) {
                            updatePermissionTreeFromDB();
                            if (cacheEntry == null) {
                                cacheEntry = new GhostResource<TreeNode>(root);
                                permissionCache.put(cacheKey, cacheEntry);
                            } else {
                                cacheEntry.setResource(root);
                            }
                            if (log.isDebugEnabled()) {
                                log.debug("Set resource to true");
                            }
                        }
                    }
                } else {
                    if(!StringUtils.isEmpty(resourceId)) {
                        //If permission tree is cached, only update the permissions of given resource path
                        synchronized (this) {
                            updateResourcePermissionsById(resourceId);
                            cacheEntry.setResource(root);
                        }
                    }
                }
            } else {
                synchronized (this) {
                    cacheEntry = (GhostResource<TreeNode>) permissionCache.get(cacheKey);
                    if (cacheEntry == null || cacheEntry.getResource() == null) {
                        updatePermissionTreeFromDB();
                        cacheKey = new PermissionTreeCacheKey(cacheIdentifier, tenantId);
                        cacheEntry = new GhostResource<TreeNode>(root);
                        try {
                            permissionCache.put(cacheKey, cacheEntry);
                        } catch (IllegalStateException e) {
                            // There is no harm ignoring cache update. as the local cache is already of no use.
                            // Mis-penalty is low.
                            String msg = "Error occurred while adding the permission tree to cache while trying to update" +
                                    " resource: " + resourceId + " in tenant: " + tenantId;
                            log.warn(msg);
                            if (log.isDebugEnabled()) {
                                log.debug(msg, e);
                            }
                        }
                        if (log.isDebugEnabled()) {
                            log.debug("Permission tree is loaded from database for the resource " + resourceId +
                                    " in tenant " + tenantId);
                        }
                    }
                }
            }
        }
    }

    /**
     * Update permission tree from cache.
     *
     * @throws org.wso2.micro.integrator.security.user.core.UserStoreException throws if fail to update permission tree from DB
     */
    void updateResourcePermissionsById(String resourceId) throws UserStoreException {
        Connection dbConnection = null;
        ResultSet rs = null;
        PreparedStatement statement = null;
        try {
            PermissionTree tree = new PermissionTree();
            tree.root = this.root;
            dbConnection = getDBConnection();
            // Populating role permissions
            if (preserveCaseForResources) {
                statement = dbConnection.prepareStatement(DBConstants.GET_EXISTING_ROLE_PERMISSIONS_BY_RESOURCE_ID_CASE_SENSITIVE);
            } else {
                statement = dbConnection.prepareStatement(DBConstants.GET_EXISTING_ROLE_PERMISSIONS_BY_RESOURCE_ID);
            }

            statement.setInt(1, tenantId);
            statement.setInt(2, tenantId);
            statement.setString(3, resourceId);
            rs = statement.executeQuery();
            write.lock();
            try {
                while (rs.next()) {
                    short allow = rs.getShort(3);
                    String roleName = rs.getString(1);
                    String domain = rs.getString(5);
                    String roleWithDomain = UserCoreUtil.addDomainToName(roleName, domain);
                    if (allow == UserCoreConstants.ALLOW) {
                        tree.authorizeRoleInTree(roleWithDomain, rs.getString(2), rs.getString(4), false);
                    }
                }
            } finally {
                this.root = tree.root;
                write.unlock();
            }
        } catch (SQLException e) {
            throw new UserStoreException(
                    "Error loading authorizations. Please check the database. Error message is "
                            + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, statement);
        }
    }

    private void invalidateCache(TreeNode root) throws UserStoreException {
        Cache<PermissionTreeCacheKey, GhostResource<TreeNode>> permissionCache = this.getPermissionTreeCache();
        if (permissionCache != null) {
            PermissionTreeCacheKey cacheKey = new PermissionTreeCacheKey(cacheIdentifier, tenantId);
            permissionCache.remove(cacheKey);
            //sending cluster message
//			CacheInvalidator invalidator = UMListenerServiceComponent.getCacheInvalidator();
//			try {
//			    if (log.isDebugEnabled()) {
//			        log.debug("Calling invalidation cache");
//			    }
//	            if (invalidator != null) {
//	                invalidator.invalidateCache(PermissionTreeCache.PERMISSION_CACHE, cacheKey);
//	            } else {
//	                if (log.isDebugEnabled()) {
//	                    log.debug("Not calling invalidation cache");
//	                }
//	            }
//	        } catch (CacheException e) {
//	            // TODO Auto-generated catch block
//	            e.printStackTrace();
//	        }
        }

    }
//////////////////////////////////////// private methods follows //////////////////////////////////////////////////

    /**
     * populate permission tree from database
     *
     * @throws org.wso2.micro.integrator.security.user.core.UserStoreException throws if fail to update permission tree from DB
     */
    void updatePermissionTreeFromDB() throws UserStoreException {
        PermissionTree tree = new PermissionTree();
        ResultSet rs = null;
        PreparedStatement prepStmt1 = null;
        PreparedStatement prepStmt2 = null;
        Connection dbConnection = null;
        try {
            dbConnection = getDBConnection();
            // Populating role permissions
            if (preserveCaseForResources) {
                prepStmt1 = dbConnection.prepareStatement(DBConstants.GET_EXISTING_ROLE_PERMISSIONS_CASE_SENSITIVE);
            } else {
                prepStmt1 = dbConnection.prepareStatement(DBConstants.GET_EXISTING_ROLE_PERMISSIONS);
            }
            prepStmt1.setInt(1, tenantId);
            prepStmt1.setInt(2, tenantId);

            rs = prepStmt1.executeQuery();

            while (rs.next()) {
                short allow = rs.getShort(3);

                String roleName = rs.getString(1);
                String domain = rs.getString(5);
                String roleWithDomain = UserCoreUtil.addDomainToName(roleName, domain);

                if (allow == UserCoreConstants.ALLOW) {
                    tree.authorizeRoleInTree(roleWithDomain, rs.getString(2), rs.getString(4), false);
                } else {
                    tree.denyRoleInTree(roleWithDomain, rs.getString(2), rs.getString(4), false);
                }
            }

            // Populating user permissions
            prepStmt2 = dbConnection.prepareStatement(DBConstants.GET_EXISTING_USER_PERMISSIONS);
            prepStmt2.setInt(1, tenantId);
            prepStmt2.setInt(2, tenantId);
            rs = prepStmt2.executeQuery();

            while (rs.next()) {
                short allow = rs.getShort(3);
                if (allow == UserCoreConstants.ALLOW) {
                    tree.authorizeUserInTree(rs.getString(1), rs.getString(2), rs.getString(4), false);
                } else {
                    tree.denyUserInTree(rs.getString(1), rs.getString(2), rs.getString(4), false);
                }

            }

            write.lock();
            try {
                this.root = tree.root;
            } finally {
                write.unlock();
            }

        } catch (SQLException e) {
            throw new UserStoreException(
                    "Error loading authorizations. Please check the database. Error message is "
                            + e.getMessage(), e);
        } finally {
            DatabaseUtil.closeAllConnections(dbConnection, rs, prepStmt1, prepStmt2);
        }
    }

    /**
     * Find a node on the tree, starting from the given nodes, and using the
     * list of path segments
     *
     * @param node      the start node to begin the search
     * @param pathParts a List of path segments - i.e. collection/resource names
     * @return the result as a SearchResult
     * @see SearchResult
     */
    private SearchResult getNode(TreeNode node, List<String> pathParts) {
        if (pathParts == null || pathParts.isEmpty()) {
            return new SearchResult(node, null);

        } else {
            String key = pathParts.get(0);
            if (key != null && key.length() > 0) {
                TreeNode child = node.getChild(key);
                if (child != null) {
                    pathParts.remove(0);
                    if (!pathParts.isEmpty()) {
                        return getNode(child, pathParts);
                    } else {
                        return new SearchResult(child, null);
                    }
                }
            }
            return new SearchResult(node, pathParts);
        }
    }

    private Connection getDBConnection() throws SQLException {
        Connection dbConnection = dataSource.getConnection();
        dbConnection.setAutoCommit(false);
        return dbConnection;
    }

    private boolean isCaseSensitiveUsername(String username, int tenantId) {

        if (UserStoreMgtDSComponent.getRealmService() != null) {
            //this check is added to avoid NullPointerExceptions if the osgi is not started yet.
            //as an example when running the unit tests.
            try {
                if (UserStoreMgtDSComponent.getRealmService().getTenantUserRealm(tenantId) != null) {
                    UserStoreManager userStoreManager = (UserStoreManager) UserStoreMgtDSComponent.getRealmService()
                            .getTenantUserRealm(tenantId).getUserStoreManager();
                    UserStoreManager userAvailableUserStoreManager = userStoreManager.getSecondaryUserStoreManager
                            (UserCoreUtil.extractDomainFromName(username));
                    String isUsernameCaseInsensitiveString = userAvailableUserStoreManager.getRealmConfiguration()
                            .getUserStoreProperty(CASE_INSENSITIVE_USERNAME);
                    return !Boolean.parseBoolean(isUsernameCaseInsensitiveString);
                }
            } catch (org.wso2.micro.integrator.security.user.api.UserStoreException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Error while reading user store property CaseInsensitiveUsername. Considering as false.");
                }
            }
        }
        return true;
    }

    private String modify(String name) {
        if (!name.contains(UserCoreConstants.DOMAIN_SEPARATOR)) {
            return name;
        }
        String domain = UserCoreUtil.extractDomainFromName(name);
        String nameWithoutDomain = UserCoreUtil.removeDomainFromName(name);
        String modifiedName = UserCoreUtil.addDomainToName(nameWithoutDomain, domain);
        return modifiedName;
    }
}
