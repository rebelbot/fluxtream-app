package org.fluxtream.mvc.controllers;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.fluxtream.core.Configuration;
import org.fluxtream.core.connectors.Connector;
import org.fluxtream.core.domain.ApiKey;
import org.fluxtream.core.domain.ApiUpdate;
import org.fluxtream.core.domain.ConnectorInfo;
import org.fluxtream.core.domain.Guest;
import org.fluxtream.core.domain.UpdateWorkerTask;
import org.fluxtream.core.mvc.models.admin.ConnectorInstanceModelFactory;
import org.fluxtream.core.services.ConnectorUpdateService;
import org.fluxtream.core.services.GuestService;
import org.fluxtream.core.services.JPADaoService;
import org.fluxtream.core.services.SystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: candide
 * Date: 17/09/13
 * Time: 12:24
 */
@Controller
public class AdminController {

    @Autowired
    GuestService guestService;

    @Autowired
    ConnectorInstanceModelFactory connectorInstanceModelFactory;

    @Autowired
    ConnectorUpdateService connectorUpdateService;

    @Autowired
    Configuration env;

    @Autowired
    SystemService systemService;

    @Autowired
    JPADaoService jpaDaoService;

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping(value = { "/admin/fail" })
    public ModelAndView getPermanentFailConnectors(HttpServletResponse response,
                                                   @RequestParam(value = "filter", required=false,
                                                                 defaultValue = "apiKey.reason!='" + ApiKey.PermanentFailReason.NEEDS_REAUTH + "'") String filter,
                                                   @RequestParam(value = "guestId", required=false) String guestId,
                                                   @RequestParam(value = "status_0", required=false, defaultValue="false") boolean statusUp,
                                                   @RequestParam(value = "status_1", required=false, defaultValue="false") boolean statusPermanentFail,
                                                   @RequestParam(value = "status_2", required=false, defaultValue="false") boolean statusTemporaryFail,
                                                   @RequestParam(value = "status_3", required=false, defaultValue="false") boolean statusOverRateLimit) throws Exception {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0.
        response.setDateHeader("Expires", 0);
        final List<ApiKey.Status> statusFilters = new ArrayList<ApiKey.Status>();
        if (statusUp)
            statusFilters.add(ApiKey.Status.STATUS_UP);
        if (statusPermanentFail)
            statusFilters.add(ApiKey.Status.STATUS_PERMANENT_FAILURE);
        if (statusTemporaryFail)
            statusFilters.add(ApiKey.Status.STATUS_TRANSIENT_FAILURE);
        if (statusOverRateLimit)
            statusFilters.add(ApiKey.Status.STATUS_OVER_RATE_LIMIT);

        ModelAndView mav = new ModelAndView("admin/permanentFail");

        final StringBuffer queryString = new StringBuffer("SELECT apiKey FROM ApiKey apiKey ");

        StringBuffer whereClause = new StringBuffer();
        boolean whereClauseAdded = addPotentialStatusWhereClause(whereClause, statusFilters);
        if (!StringUtils.isEmpty(guestId)) {
            addGuestIdClause(whereClause, guestId, whereClauseAdded);
            whereClauseAdded = true;
        }
        if (!StringUtils.isEmpty(filter)) {
            addFilterClause(whereClause, filter, whereClauseAdded);
            whereClauseAdded = true;
        }

        if (whereClauseAdded)
            queryString.append(whereClause).append(" ");

        queryString.append("ORDER BY apiKey.api, apiKey.guestId");

        System.out.println(queryString.toString());

        final List<ApiKey> apiKeys = jpaDaoService.executeQueryWithLimitAndOffset(queryString.toString(), Integer.MAX_VALUE, 0, ApiKey.class);
        mav.addObject("statusFilters", statusFilters);
        mav.addObject("apiKeys", apiKeys);
        mav.addObject("filter", filter);
        mav.addObject("guestId", guestId);
        mav.addObject("release", env.get("release"));
        return mav;
    }

    private void addFilterClause(final StringBuffer whereClause, final String filter, final boolean whereClauseAdded) {
        if (whereClauseAdded)
            whereClause.append(" AND " + filter);
        else
            whereClause.append(" WHERE " + filter);
    }

    private void addGuestIdClause(final StringBuffer whereClause, final String guestId, final boolean whereClauseAdded) {
        if (whereClauseAdded)
            whereClause.append(" AND guestId=" + guestId);
        else
            whereClause.append(" WHERE guestId=" + guestId);
    }

    private boolean addPotentialStatusWhereClause(final StringBuffer whereClause, final List<ApiKey.Status> statusFilters) {
        boolean whereClauseAdded = false;
        StringBuffer statusWhereClause = new StringBuffer();
        for (ApiKey.Status statusFilter : statusFilters) {
            addStatusWhereClauseTerm(statusWhereClause, whereClauseAdded, statusFilter.ordinal());
            whereClauseAdded = true;
        }
        if (whereClauseAdded)
            whereClause.append("WHERE ").append("(").append(statusWhereClause).append(")");
        return whereClauseAdded;
    }

    private boolean addStatusWhereClauseTerm(final StringBuffer whereClause, boolean whereClauseAdded, final int statusValue) {
        if (whereClauseAdded) whereClause.append(" OR ");
        whereClause.append("apiKey.status=" + statusValue);
        whereClauseAdded = true;
        return whereClauseAdded;
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping(value = { "/admin" })
    public ModelAndView admin(HttpServletResponse response,
                              @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                              @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) throws Exception {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0.
        response.setDateHeader("Expires", 0);
        ModelAndView mav = new ModelAndView("admin/index");

        long totalGuests = jpaDaoService.executeNativeQuery("SELECT count(*) from Guest");

        // If pageSize is too small, set to default of 20
        if(pageSize<=0)
            pageSize = 20;

        // Limit range of page to be >=1 and <= lastPage)
        int lastPage = ((int)totalGuests)%pageSize==0 ? ((int)totalGuests)/pageSize : ((int)totalGuests)/pageSize+1;
        if(page<1)
            page=1;
        else if(page>lastPage)
            page=lastPage;


        final int offset = (page - 1) * pageSize;
        final List<Guest> allGuests = jpaDaoService.executeQueryWithLimitAndOffset("SELECT guest FROM Guest guest", pageSize, offset, Guest.class);
        // get scheduled updateWorkerTasks for the current subset of users
        final List<UpdateWorkerTask> tasks = connectorUpdateService.getAllScheduledUpdateWorkerTasks();

        mav.addObject("allGuests", allGuests);
        mav.addObject("release", env.get("release"));
        final List<ConnectorInfo> connectors = systemService.getConnectors();
        mav.addObject("subview", "connectorHealthDashboard");
        mav.addObject("connectors", connectors);
        List<Map.Entry<Guest,List<List<ApiKey>>>> rows = new ArrayList<Map.Entry<Guest,List<List<ApiKey>>>>();
        ValueHolder synching = getSynchingUpdateWorkerTasks();

        long consumerTriggerRepeatInterval = Long.valueOf(env.get("consumer.trigger.repeatInterval"));
        ValueHolder due = getDueUpdateWorkerWorkerTasks(tasks, consumerTriggerRepeatInterval);
        ValueHolder overdue = getOverdueUpdateWorkerWorkerTasks(tasks, consumerTriggerRepeatInterval);

        for (Guest guest : allGuests) {
            List<List<ApiKey>> guestApiKeys = new ArrayList<List<ApiKey>>();
            for (ConnectorInfo connector : connectors) {
                final List<ApiKey> apiKeys = guestService.getApiKeys(guest.getId(), Connector.fromValue(connector.api));
                guestApiKeys.add(apiKeys);
            }
            final Map.Entry<Guest, List<List<ApiKey>>> guestListEntry = new AbstractMap.SimpleEntry<Guest, List<List<ApiKey>>>(guest, guestApiKeys);
            rows.add(guestListEntry);
        }
        mav.addObject("totalGuests", totalGuests);
        mav.addObject("fromGuest", offset);
        mav.addObject("toGuest", offset + allGuests.size());
        mav.addObject("page", page);
        mav.addObject("pageSize", pageSize);
        mav.addObject("synching", synching);
        mav.addObject("tasksDue", due);
        mav.addObject("tasksOverdue", overdue);
        mav.addObject("rows", rows);
        mav.addObject("serverUUID", connectorUpdateService.getLiveServerUUIDs().toString());
        return mav;
    }

    private ValueHolder getDueUpdateWorkerWorkerTasks(final List<UpdateWorkerTask> tasks, long consumerTriggerRepeatInterval) {
        ValueHolder due = new ValueHolder();
        for (UpdateWorkerTask task : tasks) {
            if (task.timeScheduled<=System.currentTimeMillis()-consumerTriggerRepeatInterval) {
                if (due.get(task.apiKeyId)!=null)
                    due.put(task.apiKeyId, due.get(task.apiKeyId)+1);
                else
                    due.put(task.apiKeyId, 1);
            }
        }
        return due;
    }

    private ValueHolder getOverdueUpdateWorkerWorkerTasks(final List<UpdateWorkerTask> tasks, long consumerTriggerRepeatInterval) {
        ValueHolder overdue = new ValueHolder();
        for (UpdateWorkerTask task : tasks) {
            if (task.timeScheduled>System.currentTimeMillis()-consumerTriggerRepeatInterval) {
                if (overdue.get(task.apiKeyId)!=null)
                    overdue.put(task.apiKeyId, overdue.get(task.apiKeyId)+1);
                else
                    overdue.put(task.apiKeyId, 1);
            }
        }
        return overdue;
    }

    private ValueHolder getSynchingUpdateWorkerTasks() {
        ValueHolder synching = new ValueHolder();
        final List<UpdateWorkerTask> tasks = connectorUpdateService.getAllSynchingUpdateWorkerTasks();
        for (UpdateWorkerTask task : tasks) {
            if (synching.get(task.apiKeyId)!=null)
                synching.put(task.apiKeyId, synching.get(task.apiKeyId)+1);
            else
                synching.put(task.apiKeyId, 1);
        }
        return synching;
    }

    public class ValueHolder {
        Map<Long,Integer> dict = new HashMap<Long,Integer>();
        public void put(Long l, Integer i) { dict.put(l, i); }
        public Integer get(Long l) { return dict.get(l); }
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping("/admin/{guestId}/{apiKeyId}/{objectTypes}/historyUpdate")
    public ModelAndView forceConnectorInstanceHistoryUpdate(@PathVariable("guestId") long guestId,
                                                            @PathVariable("apiKeyId") long apiKeyId,
                                                            @PathVariable("objectTypes") int objectTypes) {
        final ApiKey apiKey = guestService.getApiKey(apiKeyId);
        connectorUpdateService.updateConnectorObjectType(apiKey, objectTypes, true, true);
        return new ModelAndView(String.format("redirect:/admin/%s/%s", guestId, apiKeyId));
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping("/admin/{guestId}/{apiKeyId}/{objectTypes}/refresh")
    public ModelAndView refreshConnectorInstance(@PathVariable("guestId") long guestId,
                                                 @PathVariable("apiKeyId") long apiKeyId,
                                                 @PathVariable("objectTypes") int objectTypes) {
        final ApiKey apiKey = guestService.getApiKey(apiKeyId);
        connectorUpdateService.updateConnectorObjectType(apiKey, objectTypes, true, false);
        return new ModelAndView(String.format("redirect:/admin/%s/%s", guestId, apiKeyId));
    }

    public ModelAndView getAdminModel() {
        ModelAndView mav = new ModelAndView("admin/index");
        final List<Guest> allGuests = guestService.getAllGuests();
        mav.addObject("allGuests", allGuests);
        mav.addObject("release", env.get("release"));
        return mav;
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping(value = "/admin/{guestId}/{apiKeyId}/setToPermanentFail")
    public ModelAndView setToPermanentFail(@PathVariable("guestId") long guestId,
                                           @PathVariable("apiKeyId") long apiKeyId) throws Exception {
        guestService.setApiKeyStatus(apiKeyId, ApiKey.Status.STATUS_PERMANENT_FAILURE, null, ApiKey.PermanentFailReason.MANUAL_INTERVENTION);
        return new ModelAndView(String.format("redirect:/admin/%s/%s", guestId, apiKeyId));
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping("/admin/{guestId}/{apiKeyId}")
    public ModelAndView showConnectorInstanceDetails(@PathVariable("guestId") long guestId,
                                                     @PathVariable("apiKeyId") long apiKeyId) {
        ModelAndView mav = getAdminModel();
        mav.addObject("subview", "connectorDetails");
        final ApiKey apiKey = guestService.getApiKey(apiKeyId);
        final Map<String, Object> connectorInstanceModel = connectorInstanceModelFactory.createConnectorInstanceModel(apiKey);
        final Guest guest = guestService.getGuestById(guestId);
        final List<ApiUpdate> lastUpdates = connectorUpdateService.getUpdates(apiKey, 100, 0);
        mav.addObject("guest", guest);
        mav.addObject("guestId", guest.getId());
        mav.addObject("apiKeyId", apiKeyId);
        mav.addObject("apiKey", apiKey);
        mav.addObject("attributes", guestService.getApiKeyAttributes(apiKeyId));
        mav.addObject("connectorInstanceModel", connectorInstanceModel);
        mav.addObject("lastUpdates", lastUpdates);
        mav.addObject("liveServerUUIDs", connectorUpdateService.getLiveServerUUIDs());
        List<UpdateWorkerTask> scheduledTasks = getScheduledTasks(apiKey);
        mav.addObject("scheduledTasks", scheduledTasks);
        return mav;
    }

    private List<UpdateWorkerTask> getScheduledTasks(final ApiKey apiKey) {
        final int[] objectTypeValues = apiKey.getConnector().objectTypeValues();
        List<UpdateWorkerTask> scheduledTasks = new ArrayList<UpdateWorkerTask>();
        if (apiKey.getConnector().isAutonomous()) {
            final List<UpdateWorkerTask> updateWorkerTasks = connectorUpdateService.getUpdateWorkerTasks(apiKey, 0, 10);
            scheduledTasks.addAll(updateWorkerTasks);
        } else {
            for (int objectTypeValue : objectTypeValues) {
                final List<UpdateWorkerTask> updateWorkerTasks = connectorUpdateService.getUpdateWorkerTasks(apiKey, objectTypeValue, 10);
                scheduledTasks.addAll(updateWorkerTasks);
            }
        }
        Collections.sort(scheduledTasks, new Comparator<UpdateWorkerTask>() {

            @Override
            public int compare(final UpdateWorkerTask o1, final UpdateWorkerTask o2) {
                return (int)(o2.timeScheduled - o1.timeScheduled);
            }
        });
        return scheduledTasks;
    }

    @Secured({ "ROLE_ADMIN" })
    @RequestMapping("/admin/{guestId}")
    public ModelAndView showUserApiKeys(@PathVariable("guestId") long guestId) {
        ModelAndView mav = getAdminModel();
        mav.addObject("subview", "allConnectors");
        final Guest guest = guestService.getGuestById(guestId);
        final List<ApiKey> apiKeys = guestService.getApiKeys(guest.getId());
        mav.addObject("username", guest.username);
        mav.addObject("guestId", guest.getId());
        mav.addObject("connectorInstanceModels", getConnectorInstanceModels(apiKeys));
        return mav;
    }

    private Object getConnectorInstanceModels(final List<ApiKey> apiKeys) {
        Map<Long, Map<String,Object>> connectorInstanceModels = new HashMap<Long, Map<String,Object>>();
        for (ApiKey key : apiKeys) {
            final Map<String, Object> connectorInstanceModel = connectorInstanceModelFactory.createConnectorInstanceModel(key);
            connectorInstanceModels.put(key.getId(), connectorInstanceModel);
        }
        return connectorInstanceModels;
    }

}
