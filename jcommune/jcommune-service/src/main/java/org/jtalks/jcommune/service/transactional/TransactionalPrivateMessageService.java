/**
 * Copyright (C) 2011  jtalks.org Team
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * Also add information on how to contact you by electronic and paper mail.
 * Creation date: Apr 12, 2011 / 8:05:19 PM
 * The jtalks.org Project
 */
package org.jtalks.jcommune.service.transactional;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.jtalks.jcommune.model.dao.PrivateMessageDao;
import org.jtalks.jcommune.model.entity.PrivateMessage;
import org.jtalks.jcommune.model.entity.PrivateMessageStatus;
import org.jtalks.jcommune.model.entity.User;
import org.jtalks.jcommune.service.PrivateMessageService;
import org.jtalks.jcommune.service.SecurityService;
import org.jtalks.jcommune.service.UserService;
import org.jtalks.jcommune.service.exceptions.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * The implementation of PrivateMessageServices.
 *
 * @author Pavel Vervenko
 * @author Kirill Afonin
 * @author Max Malakhov
 */
public class TransactionalPrivateMessageService
        extends AbstractTransactionalEntityService<PrivateMessage, PrivateMessageDao> implements PrivateMessageService {

    private final SecurityService securityService;
    private final UserService userService;
    private final Ehcache userDataCache;

    /**
     * Creates the instance of service.
     *
     * @param pmDao           PrivateMessageDao
     * @param securityService for retrieving current user
     * @param userService     for getting user by name
     * @param userDataCache   cache for user data
     */
    public TransactionalPrivateMessageService(PrivateMessageDao pmDao,
                                              SecurityService securityService,
                                              UserService userService,
                                              Ehcache userDataCache) {
        this.dao = pmDao;
        this.securityService = securityService;
        this.userService = userService;
        this.userDataCache = userDataCache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PrivateMessage> getInboxForCurrentUser() {
        User currentUser = securityService.getCurrentUser();
        return dao.getAllForUser(currentUser);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PrivateMessage> getOutboxForCurrentUser() {
        User currentUser = securityService.getCurrentUser();
        return dao.getAllFromUser(currentUser);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN')")
    public PrivateMessage sendMessage(String title, String body, String recipientUsername) throws NotFoundException {
        User recipient = userService.getByUsername(recipientUsername);
        PrivateMessage pm = populateMessage(title, body, recipient);
        pm.setStatus(PrivateMessageStatus.NOT_READED);
        dao.saveOrUpdate(pm);
        incrementNewMessageCountInCacheFor(recipientUsername);

        securityService.grantReadPermissionToCurrentUser(pm);
        securityService.grantReadPermissionToUser(pm, recipientUsername);

        return pm;
    }

    /**
     * Populate {@link PrivateMessage} from values.
     *
     * @param title     title
     * @param body      message content
     * @param recipient message recipient
     * @return created {@link PrivateMessage}
     * @throws NotFoundException if current user of recipient not found
     */
    private PrivateMessage populateMessage(String title, String body,
                                           User recipient) throws NotFoundException {
        PrivateMessage pm = PrivateMessage.createNewPrivateMessage();
        pm.setTitle(title);
        pm.setBody(body);
        pm.setUserFrom(securityService.getCurrentUser());
        pm.setUserTo(recipient);
        return pm;
    }

    /**
     * {@inheritDoc}
     */
    public void markAsReaded(PrivateMessage pm) {
        pm.markAsReaded();
        dao.saveOrUpdate(pm);
        decrementNewMessageCountInCacheFor(pm.getUserTo().getUsername());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PrivateMessage> getDraftsFromCurrentUser() {
        User currentUser = securityService.getCurrentUser();
        return dao.getDraftsFromUser(currentUser);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN')")
    public PrivateMessage saveDraft(long id, String title, String body, String recipientUsername)
            throws NotFoundException {
        User recipient = userService.getByUsername(recipientUsername);
        PrivateMessage pm = populateMessage(title, body, recipient);
        pm.setId(id);
        pm.markAsDraft();
        dao.saveOrUpdate(pm);

        securityService.grantAdminPermissionToCurrentUser(pm);

        return pm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int currentUserNewPmCount() {
        String username = securityService.getCurrentUserUsername();
        if (username == null || username.equals("anonymousUser")) {
            return 0;
        }

        if (userDataCache.isKeyInCache(username)) {
            return (Integer) userDataCache.get(username).getValue();
        }
        int count = dao.getNewMessagesCountFor(username);
        userDataCache.put(new Element(username, count));
        return count;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @PreAuthorize("hasPermission(#id, 'org.jtalks.jcommune.model.entity.PrivateMessage', admin)")
    public PrivateMessage sendDraft(long id, String title, String body,
                                    String recipientUsername) throws NotFoundException {
        User recipient = userService.getByUsername(recipientUsername);
        PrivateMessage pm = populateMessage(title, body, recipient);
        pm.setId(id);
        pm.setStatus(PrivateMessageStatus.NOT_READED);
        dao.saveOrUpdate(pm);
        incrementNewMessageCountInCacheFor(recipientUsername);

        securityService.deleteFromAcl(pm);
        securityService.grantReadPermissionToCurrentUser(pm);
        securityService.grantReadPermissionToUser(pm, recipientUsername);

        return pm;
    }

    /**
     * Increment new private messages count for user in cache.
     * Only if user in cache.
     *
     * @param username username (cache key)
     */
    private void incrementNewMessageCountInCacheFor(String username) {
        if (userDataCache.isKeyInCache(username)) {
            int count = (Integer) userDataCache.get(username).getValue();
            count++;
            userDataCache.put(new Element(username, count));
        }
    }

    /**
     * Decrement new private messages count for user in cache.
     * Only if user in cache.
     *
     * @param username username  (cache key)
     */
    private void decrementNewMessageCountInCacheFor(String username) {
        if (userDataCache.isKeyInCache(username)) {
            int count = (Integer) userDataCache.get(username).getValue();
            count--;
            userDataCache.put(new Element(username, count));
        }
    }
    
    /**
     * {@inheritDoc}
     */    
    @Override
    @PreAuthorize("hasPermission(#id, 'org.jtalks.jcommune.model.entity.PrivateMessage', admin) or " +
            "hasPermission(#id, 'org.jtalks.jcommune.model.entity.PrivateMessage', read)")
    public PrivateMessage get(Long id) throws NotFoundException {
        return super.get(id);
    }
}
