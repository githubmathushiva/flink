/*
 * Copyright 2019 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webank.wedatasphere.linkis.bml.restful;

import com.webank.wedatasphere.linkis.bml.Entity.DownloadModel;
import com.webank.wedatasphere.linkis.bml.Entity.Resource;
import com.webank.wedatasphere.linkis.bml.Entity.ResourceTask;
import com.webank.wedatasphere.linkis.bml.Entity.ResourceVersion;
import com.webank.wedatasphere.linkis.bml.Entity.Version;
import com.webank.wedatasphere.linkis.bml.common.Constant;
import com.webank.wedatasphere.linkis.bml.service.BmlService;
import com.webank.wedatasphere.linkis.bml.service.DownloadService;
import com.webank.wedatasphere.linkis.bml.service.ResourceService;
import com.webank.wedatasphere.linkis.bml.service.TaskService;
import com.webank.wedatasphere.linkis.bml.service.VersionService;
import com.webank.wedatasphere.linkis.bml.threading.TaskState;
import com.webank.wedatasphere.linkis.bml.util.HttpRequestHelper;
import com.webank.wedatasphere.linkis.bml.vo.ResourceBasicVO;
import com.webank.wedatasphere.linkis.bml.vo.ResourceVO;
import com.webank.wedatasphere.linkis.bml.vo.ResourceVersionsVO;
import com.webank.wedatasphere.linkis.common.exception.ErrorException;
import com.webank.wedatasphere.linkis.server.Message;
import com.webank.wedatasphere.linkis.bml.common.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;


@Path("bml")
@Component
public class BmlRestfulApi {

    @Autowired
    private BmlService bmlService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private DownloadService downloadService;

    @Autowired
    private TaskService taskService;

    private Logger logger = LoggerFactory.getLogger(getClass());

    public static final String URL_PREFIX = "/bml/";

    @GET
    @Path("getVersions")
    public Response getVersions(@QueryParam("resourceId") String resourceId,
                                @QueryParam("currentPage") String  currentPage,
                                @QueryParam("pageSize") String pageSize,
                                @Context HttpServletRequest request)throws ErrorException{

        String user = RestfulUtils.getUserName(request);
        if (StringUtils.isEmpty(resourceId) || !resourceService.checkResourceId(resourceId)){
            logger.error("ResourceId {} provided by user {} is illegal (??????{} ?????????resourceId {} is illegal)", resourceId,user,user, resourceId );
            throw new BmlServerParaErrorException("The ResourceID you submitted is invalid (????????????resourceId??????)");
        }


        Integer current = 0;
        Integer size = 0;
        if (StringUtils.isEmpty(currentPage)  || !StringUtils.isNumeric(currentPage)){
            current = 1;
        }else{
            current = Integer.valueOf(currentPage);
        }
        if ( StringUtils.isEmpty(pageSize )  || !StringUtils.isNumeric(pageSize) ){
            size = 10;
        }else{
            size = Integer.valueOf(pageSize);
        }


        Message message = null;

        try{
            logger.info("User {} starts getting information about all versions of {} (?????? {} ???????????? {} ?????????????????????)", user, resourceId,user, resourceId);
            List<Version> versionList = versionService.selectVersionByPage(current, size, resourceId);
            if (versionList.size() > 0){
                message = Message.ok("Version information obtained successfully (????????????????????????)");
                message.setMethod(URL_PREFIX + "getVersions");
                message.setStatus(0);
                ResourceVersionsVO resourceVersionsVO = new ResourceVersionsVO();
                resourceVersionsVO.setVersions(versionList);
                resourceVersionsVO.setResourceId(resourceId);
                resourceVersionsVO.setUser(user);
                message.data("ResourceVersions", resourceVersionsVO);
            }else{
                logger.warn("User {} fetch resource {} no error, but fetch version length 0 (user {} ????????????{}??????????????????????????????version?????????0)", user, resourceId, user, resourceId);
                message = Message.error("Failed to get version information correctly(?????????????????????????????????)");
                message.setMethod(URL_PREFIX + "getVersions");
                message.setStatus(2);
            }
            logger.info("User {} ends getting all version information for {} (?????? {} ???????????? {} ?????????????????????)", user, resourceId, user, resourceId);
        }catch(final Exception e){
            logger.error("User {} Failed to get version information of the ResourceId {} resource(user {} ??????resourceId {} ???????????????????????????)", user, resourceId, user, resourceId, e);
            throw new BmlQueryFailException("Sorry, the query for version information failed(?????????????????????????????????)");
        }

        return Message.messageToResponse(message);
    }

    @GET
    @Path("getResources")
    public Response getResources(@QueryParam("system") String system,
                                 @QueryParam("currentPage") String  currentPage,
                                 @QueryParam("pageSize") String pageSize,
                                 @Context HttpServletRequest request,
                                 @Context HttpServletResponse response)throws ErrorException {

        String user = RestfulUtils.getUserName(request);

        if (StringUtils.isEmpty(system)) {
            //???????????????wtss
            system = Constant.DEFAULT_SYSTEM;
        }

        Integer current = 0;
        Integer size = 0;
        if (StringUtils.isEmpty(currentPage)  || !StringUtils.isNumeric(currentPage)){
            current = 1;
        }else{
            current = Integer.valueOf(currentPage);
        }
        if ( StringUtils.isEmpty(pageSize )  || !StringUtils.isNumeric(pageSize) ){
            size = 10;
        }else{
            size = Integer.valueOf(pageSize);
        }
        Message message = null;
        try{
            logger.info("User {} starts fetching all the resources of the system {}(?????? {} ?????????????????? {} ???????????????)", user, system, user, system);
            List<ResourceVersion> resourceVersionPageInfoList  = versionService.selectResourcesViaSystemByPage(current, size, system, user);
            if (resourceVersionPageInfoList.size() > 0){
                message = Message.ok("Get all your resources in system "+ system +" successfully(??????????????????" + system + "?????????????????????)");
                message.setStatus(0);
                message.setMethod(URL_PREFIX + "getResources");
                List<ResourceVO> resourceVOList = new ArrayList<>();
                for(ResourceVersion resourceVersion : resourceVersionPageInfoList){
                    ResourceVO resourceVO = new ResourceVO();
                    resourceVO.setResource(resourceVersion.getResource());
                    resourceVO.setUser(user);
                    resourceVO.setResourceId(resourceVersion.getResourceId());
                    resourceVO.setVersion(resourceVersion.getVersion());
                    resourceVOList.add(resourceVO);
                }
                message.data("Resources", resourceVOList);
            }else{
                logger.warn("User {} gets system {} resource size of 0(?????? {} ???????????? {} ?????????size???0)", user, system, user, system);
                message = Message.error("Failed to obtain all resource information(???????????????????????????????????????)");
                message.setStatus(2);
                message.setMethod(URL_PREFIX + "getResources");
            }
            logger.info("User {} ends fetching all resources for system {}(?????? {} ?????????????????? {} ???????????????)", user, system,user, system);
        }catch(final Exception e){
            logger.error("User {} failed to obtain all resources of the system {}(?????? {} ???????????? {} ??????????????????).",user, system,user, system, e);
            throw new BmlQueryFailException("Failed to get all system resource information(????????????????????????????????????)");
        }

        return Message.messageToResponse(message);
    }



    @POST
    @Path("deleteVersion")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteVersion(JsonNode jsonNode,
                                  @Context HttpServletRequest request) throws IOException, ErrorException{


        String user = RestfulUtils.getUserName(request);

        if (null == jsonNode.get("resourceId") || null == jsonNode.get("version") ||
                StringUtils.isEmpty(jsonNode.get("resourceId").getTextValue()) || StringUtils.isEmpty(jsonNode.get("version").getTextValue())) {
            throw new BmlServerParaErrorException("ResourceID and version are required to delete the specified version(?????????????????????????????????resourceId ??? version)");
        }



        String resourceId = jsonNode.get("resourceId").getTextValue();
        String version = jsonNode.get("version").getTextValue();
        //?????????????????????????????????
        if (!resourceService.checkResourceId(resourceId) || !versionService.checkVersion(resourceId, version)
                || !versionService.canAccess(resourceId, version)){
            throw new BmlServerParaErrorException("The passed ResourceID or version is illegal or has been deleted(?????????resourceId???version??????,????????????)");
        }
        Message message = null;
        ResourceTask resourceTask = taskService.createDeleteVersionTask(resourceId, version, user, HttpRequestHelper.getIp(request));
        try{
            logger.info("User {} starts to delete resource of ResourceID: {} version: {}(?????? {} ???????????? resourceId: {} version: {} ?????????)",user,resourceId, version ,user,resourceId, version);
            versionService.deleteResourceVersion(resourceId, version);
            message = Message.ok("Deleted version successfully(??????????????????)");
            message.setMethod(URL_PREFIX + "deleteVersion");
            message.setStatus(0);
            logger.info("User {} ends deleting the resourceID: {} version: {} resource(?????? {} ???????????? resourceId: {} version: {} ?????????)",user, resourceId, version,user, resourceId, version);
            taskService.updateState(resourceTask.getId(), TaskState.SUCCESS.getValue(), new Date());
            logger.info("Update task tasKid :{} -ResourceId :{} with {} state?????????????????????.???????????? taskId:{}-resourceId:{} ??? {} ?????????.", resourceTask.getId(), resourceTask.getResourceId(), TaskState.SUCCESS.getValue(),resourceTask.getId(), resourceTask.getResourceId(), TaskState.SUCCESS.getValue());
        }catch(final Exception e){
            logger.error("User {} failed to delete resource {}, version {}(??????{}??????resource {}, version {} ??????)", user, resourceId, version,user, resourceId, version, e);
            taskService.updateState2Failed(resourceTask.getId(), TaskState.FAILED.getValue(), new Date(), e.getMessage());
            logger.info("Update task tasKid :{} -ResourceId :{} with {} state?????????????????????.???????????? taskId:{}-resourceId:{} ??? {} ?????????.", resourceTask.getId(), resourceTask.getResourceId(), TaskState.SUCCESS.getValue(),resourceTask.getId(), resourceTask.getResourceId(), TaskState.SUCCESS.getValue());
            throw new BmlQueryFailException("Failed to delete the resource version(????????????????????????)");
        }
        return Message.messageToResponse(message);
    }

    @POST
    @Path("deleteResource")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteResource(JsonNode jsonNode,
                                   @Context HttpServletRequest request) throws IOException, ErrorException{


        String user = RestfulUtils.getUserName(request);

        if (null == jsonNode.get("resourceId")) {
            throw new BmlServerParaErrorException("You did not pass a valid ResourceID(?????????????????????resourceId)");
        }

        String resourceId = jsonNode.get("resourceId").getTextValue();
        if (StringUtils.isEmpty(resourceId) || !resourceService.checkResourceId(resourceId)) {
            logger.error("the error resourceId  is {} ", resourceId);
            throw new BmlServerParaErrorException("the resourceId"+resourceId+" is null ,Illegal or deleted (resourceId:"+resourceId+"??????,????????????????????????!)");
        }

        Message message = null;
        ResourceTask resourceTask = taskService.createDeleteResourceTask(resourceId, user, HttpRequestHelper.getIp(request));
        try{
            logger.info("User {} starts to delete all resources corresponding to ResourceId: {}(?????? {}  ???????????? resourceId: {} ?????????????????????)", user, resourceId, user, resourceId);
            resourceService.deleteResource(resourceId);
            message = Message.ok("Resource deleted successfully(??????????????????)");
            message.setMethod(URL_PREFIX + "deleteResource");
            message.setStatus(0);
            logger.info("User {} ends deleting all resources corresponding to ResourceId: {}(?????? {}  ???????????? resourceId: {} ?????????????????????)", user, resourceId, user, resourceId);
            taskService.updateState(resourceTask.getId(), TaskState.SUCCESS.getValue(), new Date());
            logger.info("Resource deleted successfully. Update task tasKid :{} -ResourceId :{} with {} state (??????????????????.???????????? taskId:{}-resourceId:{} ??? {} ??????).", resourceTask.getId(), resourceTask.getResourceId(), TaskState.SUCCESS.getValue(),resourceTask.getId(), resourceTask.getResourceId(), TaskState.SUCCESS.getValue());
        }catch(final Exception e){
            logger.error("User {} failed to delete resource {}(?????? {} ???????????? {} ??????)", user, resourceId, user, resourceId);
            taskService.updateState2Failed(resourceTask.getId(), TaskState.FAILED.getValue(), new Date(), e.getMessage());
            logger.info("Failed to delete resource. Update task tasKid :{} -ResourceId :{} is in {} state(??????????????????.???????????? taskId:{}-resourceId:{} ??? {} ??????.)", resourceTask.getId(), resourceTask.getResourceId(), TaskState.FAILED.getValue(),resourceTask.getId(), resourceTask.getResourceId(), TaskState.FAILED.getValue());
            throw new BmlQueryFailException("Delete resource operation failed(????????????????????????)");
        }

        return Message.messageToResponse(message);

    }

    @POST
    @Path("deleteResources")
    public Response deleteResources(JsonNode jsonNode,
                                    @Context HttpServletRequest request) throws IOException, ErrorException{
        String user = RestfulUtils.getUserName(request);
        List<String> resourceIds = new ArrayList<>();

        if (null == jsonNode.get("resourceIds")) {
            throw new BmlServerParaErrorException("Bulk deletion of unpassed resourceIDS parameters(?????????????????????resourceIds??????)");
        }

        Iterator<JsonNode> jsonNodeIter = jsonNode.get("resourceIds").getElements();
        while (jsonNodeIter.hasNext()) {
            resourceIds.add(jsonNodeIter.next().asText());
        }

        if (0 == resourceIds.size()) {
            throw new BmlServerParaErrorException("Bulk deletion of  resourceIDS parameters is null(?????????????????????????????????resourceIds????????????)");
        }else{
            for (String resourceId:  resourceIds) {
                if (StringUtils.isBlank(resourceId) || !resourceService.checkResourceId(resourceId)) {
                    Message message = Message.error("ResourceID :"+ resourceId +" is empty, illegal or has been deleted(resourceId:"+resourceId+"??????,?????????????????????)");
                    message.setMethod(URL_PREFIX + "deleteResources");
                    message.setStatus(1);
                    return Message.messageToResponse(message);
                }
            }
        }

        ResourceTask resourceTask = taskService.createDeleteResourcesTask(resourceIds, user, HttpRequestHelper.getIp(request));
        Message message = null;
        try{
            logger.info("User {} begins to batch delete resources (?????? {} ?????????????????????) ", user,user);
            resourceService.batchDeleteResources(resourceIds);
            message = Message.ok("Batch deletion of resource was successful(????????????????????????)");
            message.setMethod(URL_PREFIX + "deleteResources");
            message.setStatus(0);
            logger.info("User {} ends the bulk deletion of resources (?????? {} ????????????????????????)", user,user);
            taskService.updateState(resourceTask.getId(), TaskState.SUCCESS.getValue(), new Date());
            logger.info("Batch deletion of resource was successful. Update task tasKid :{} -ResourceId :{} is in the {} state (????????????????????????.???????????? taskId:{}-resourceId:{} ??? {} ??????.)", resourceTask.getId(), resourceTask.getResourceId(), TaskState.SUCCESS.getValue(),resourceTask.getId(), resourceTask.getResourceId(), TaskState.SUCCESS.getValue());
        }catch(final Exception e){
            logger.error("\"User {} failed to delete resources in bulk (?????? {} ????????????????????????)",user, user, e);
            taskService.updateState2Failed(resourceTask.getId(), TaskState.FAILED.getValue(), new Date(), e.getMessage());
            logger.info("Failed to delete resources in bulk. Update task tasKid :{} -ResourceId :{} is in the {} state (????????????????????????.???????????? taskId:{}-resourceId:{} ??? {} ??????.)", resourceTask.getId(), resourceTask.getResourceId(), TaskState.FAILED.getValue(),resourceTask.getId(), resourceTask.getResourceId(), TaskState.FAILED.getValue());
            throw new BmlQueryFailException("The bulk delete resource operation failed(??????????????????????????????)");
        }
        return Message.messageToResponse(message);
    }

    /**
     * ??????resourceId ??? version???????????????????????????????????????
     * @param resourceId ??????Id
     * @param version ????????????????????????????????????????????????
     * @param resp httpServletResponse
     * @param request httpServletRequest
     * @return Response
     * @throws IOException
     * @throws ErrorException
     */
    @GET
    @Path("download")
    public Response download(@QueryParam("resourceId") String resourceId,
                             @QueryParam("version") String version,
                             @Context HttpServletResponse resp,
                             @Context HttpServletRequest request) throws IOException, ErrorException {
        String user = RestfulUtils.getUserName(request);

        if (StringUtils.isBlank(resourceId) || !resourceService.checkResourceId(resourceId)) {
            Message message = Message.error("ResourceID :"+ resourceId +" is empty, illegal or has been deleted (resourceId:"+resourceId+"??????,????????????????????????!)");
            message.setMethod(URL_PREFIX + "download");
            message.setStatus(1);
            return Message.messageToResponse(message);
        }

        if (!resourceService.checkAuthority(user, resourceId)){
            throw new BmlPermissionDeniedException("You do not have permission to download this resource (??????????????????????????????)");
        }
        //???version???,??????????????????
        if (StringUtils.isBlank(version)){
            version = versionService.getNewestVersion(resourceId);
        }
        //???version?????????????????????
        if (!versionService.checkVersion(resourceId, version)) {
            Message message = Message.error("version:"+version+"is empty, illegal or has been deleted");
            message.setMethod(URL_PREFIX + "download");
            message.setStatus(1);
            return Message.messageToResponse(message);
        }
        //???resourceId???version????????????
        if (!resourceService.checkExpire(resourceId, version)){
            throw new BmlResourceExpiredException(resourceId);
        }

        Message message = null;
        resp.setContentType("application/x-msdownload");
        resp.setHeader("Content-Disposition", "attachment");
        String ip = HttpRequestHelper.getIp(request);
        DownloadModel downloadModel = new DownloadModel(resourceId, version,user, ip);
        try{
            logger.info("user {} downLoad resource  resourceId is {}, version is {} ,ip is {} ", user, resourceId, version, ip);
            Map<String, Object> properties = new HashMap<>();
            boolean downloadResult = versionService.downloadResource(user, resourceId, version, resp.getOutputStream(), properties);
            downloadModel.setEndTime(new Date(System.currentTimeMillis()));
            downloadModel.setState(0);
            if (downloadResult){
                message = Message.ok("Download resource successfully (??????????????????)");
                message.setStatus(0);
                message.setMethod(URL_PREFIX + "download");
            }else{
                logger.warn("ResourceId :{}, version:{} has a problem when user {} downloads the resource. The copied size is less than 0(?????? {} ???????????? resourceId: {}, version:{} ????????????,?????????size??????0)", resourceId,version ,user, user,resourceId, version);
                downloadModel.setState(1);
                message = Message.error("Failed to download the resource(??????????????????)");
                message.setStatus(1);
                message.setMethod(URL_PREFIX + "download");
            }
            downloadService.addDownloadRecord(downloadModel);
            logger.info("User {} ends downloading the resource {}(?????? {} ?????????????????? {}) ", user, resourceId,user, resourceId);
        }catch(IOException e){
            logger.error("IO Exception: ResourceId :{}, version:{} (?????? {} ???????????? resourceId: {}, version:{} ??????IO??????)",  resourceId, version,user, resourceId, version, e);
            downloadModel.setEndTime(new Date());
            downloadModel.setState(1);
            downloadService.addDownloadRecord(downloadModel);
            throw new ErrorException(73562, "Sorry, the background IO error caused you to download the resources failed(??????,??????IO???????????????????????????????????????)");
        }catch(final Throwable t){
            logger.error("ResourceId :{}, version:{} abnormal when user {} downloads resource (?????? {} ???????????? resourceId: {}, version:{} ????????????)",resourceId, version,user, user, resourceId, version, t);
            downloadModel.setEndTime(new Date());
            downloadModel.setState(1);
            downloadService.addDownloadRecord(downloadModel);
            throw new ErrorException(73561, "Sorry, the background service error caused you to download the resources failed(????????????????????????????????????????????????????????????)");
        }finally{
            IOUtils.closeQuietly(resp.getOutputStream());
        }
        logger.info("{} Download resource {} successfully {} ???????????? {} ??????", user, resourceId, user, resourceId);
        return Message.messageToResponse(message);
    }

    @POST
    @Path("upload")
    public Response uploadResource(@Context HttpServletRequest req,
                                   @FormDataParam("system") String system,
                                   @FormDataParam("resourceHeader") String resourceHeader,
                                   @FormDataParam("isExpire") String isExpire,
                                   @FormDataParam("expireType") String expireType,
                                   @FormDataParam("expireTime") String expireTime,
                                   @FormDataParam("maxVersion") int maxVersion,
                                   FormDataMultiPart form) throws ErrorException {
        String user = RestfulUtils.getUserName(req);
        Message message;
        try{
            logger.info("User {} starts uploading resources (?????? {} ??????????????????)", user,user);
            Map<String, Object> properties = new HashMap<>();
            properties.put("system", system);
            properties.put("resourceHeader", resourceHeader);
            properties.put("isExpire", isExpire);
            properties.put("expireType", expireType);
            properties.put("expireTime", expireTime);
            properties.put("maxVersion", maxVersion);
            String clientIp = HttpRequestHelper.getIp(req);
            properties.put("clientIp", clientIp);
            ResourceTask resourceTask = taskService.createUploadTask(form, user, properties);
            message = Message.ok("The task of submitting and uploading resources was successful(??????????????????????????????)");
            message.setMethod(URL_PREFIX + "upload");
            message.setStatus(0);
            message.data("resourceId", resourceTask.getResourceId());
            message.data("version", resourceTask.getVersion());
            message.data("taskId", resourceTask.getId());
            logger.info("User {} submitted upload resource task successfully(?????? {} ??????????????????????????????, resourceId is {})", user,user, resourceTask.getResourceId());
        } catch(final Exception e){
            logger.error("upload resource for user : {} failed, reason:", user, e);
            ErrorException exception = new ErrorException(50073, "The commit upload resource task failed(??????????????????????????????):" + e.getMessage());
            exception.initCause(e);
            throw exception;
        }
        return Message.messageToResponse(message);
    }

    /**
     * ????????????http???????????????????????????
     * @param request httpServletRequest
     * @param resourceId ???????????????????????????resourceId
     * @param formDataMultiPart form????????????
     * @return resourceId ?????? ???????????????
     */
    @POST
    @Path("updateVersion")
    public Response updateVersion(@Context HttpServletRequest request,
                                  @FormDataParam("resourceId") String resourceId,
                                  FormDataMultiPart formDataMultiPart)throws Exception{
        String user = RestfulUtils.getUserName(request);
        if (StringUtils.isEmpty(resourceId) || !resourceService.checkResourceId(resourceId)) {
            logger.error("error resourceId  is {} ", resourceId);
            throw new BmlServerParaErrorException("resourceId: " + resourceId + "is Null, illegal, or deleted!");
        }
        if (StringUtils.isEmpty(versionService.getNewestVersion(resourceId))) {
            logger.error("If the material has not been uploaded or has been deleted, please call the upload interface first .(resourceId:{} ?????????????????????,?????????????????????,????????????????????????.)", resourceId);
            throw new BmlServerParaErrorException("If the material has not been uploaded or has been deleted, please call the upload interface first( resourceId: " + resourceId + " ?????????????????????,?????????????????????,????????????????????????.!)");
        }
        Message message;
        try{
            logger.info("User {} starts updating resources {}(?????? {} ?????????????????? {}) ", user, resourceId, user, resourceId);
            String clientIp = HttpRequestHelper.getIp(request);
            Map<String, Object> properties = new HashMap<>();
            properties.put("clientIp", clientIp);
            ResourceTask resourceTask = null;
            synchronized (resourceId.intern()){
                resourceTask = taskService.createUpdateTask(resourceId, user, formDataMultiPart, properties);
            }
            message = Message.ok("The update resource task was submitted successfully(??????????????????????????????)");
            message.data("resourceId",resourceId).data("version", resourceTask.getVersion()).data("taskId", resourceTask.getId());
        }catch(final ErrorException e){
            logger.error("{} update resource failed, resourceId is {}, reason:", user, resourceId, e);
            throw e;
        } catch(final Exception e){
            logger.error("{} update resource failed, resourceId is {}, reason:", user, resourceId, e);
            ErrorException exception = new ErrorException(50073, "The commit upload resource task failed(??????????????????????????????):" + e.getMessage());
            exception.initCause(e);
            throw exception;
        }
        logger.info("User {} ends updating resources {}(?????? {} ?????????????????? {}) ", user, resourceId, user, resourceId);
        return Message.messageToResponse(message);
    }


    @GET
    @Path("getBasic")
    public Response getBasic(@QueryParam("resourceId") String resourceId,
                             @Context HttpServletRequest request)throws ErrorException{
        String user = RestfulUtils.getUserName(request);

        if (StringUtils.isEmpty(resourceId) || !resourceService.checkResourceId(resourceId)){
            throw new BmlServerParaErrorException("The basic information of the resource is not passed into the ResourceId parameter or the parameter is illegal(?????????????????????????????????resourceId?????????????????????)");
        }

        Message message = null;
        try{
            Resource resource = resourceService.getResource(resourceId);
            //int numberOfVersions = versionService.getNumOfVersions(resourceId);
            if (!resource.isEnableFlag()){
                logger.warn("The resource {} that user {} wants to query has been deleted (?????? {} ????????????????????? {} ???????????????)", user, resourceId, user, resourceId);
                message = Message.error("The resource has been deleted(?????????????????????)");
            }else{
                logger.info("User {} starts getting basic information about {}(?????? {} ???????????? {} ???????????????)", user, resourceId, user, resourceId);
                ResourceBasicVO basic = new ResourceBasicVO();
                basic.setResourceId(resourceId);
                basic.setCreateTime(resource.getCreateTime());
                basic.setDownloadedFileName(resource.getDownloadedFileName());
                basic.setOwner(resource.getUser());
                //todo cooperyang ?????????????????????
                basic.setNumberOfVerions(10);
                if (resource.isExpire()){
                    basic.setExpireTime(RestfulUtils.getExpireTime(resource.getCreateTime(),resource.getExpireType(), resource.getExpireTime()));
                }else{
                    basic.setExpireTime("Resource not expired(???????????????)");
                }
                message = Message.ok("Acquisition of resource basic information successfully(??????????????????????????????)");
                message.setStatus(0);
                message.setMethod(URL_PREFIX + "getBasic");
                message.data("basic", basic);
                logger.info("User {} ends fetching basic information for {}(?????? {} ???????????? {} ???????????????)", user, resourceId, user, resourceId);
            }
        }catch(final Exception e){
            logger.error("?????? {} ?????? {} ??????????????????", user, resourceId, e);
            throw new BmlQueryFailException("Failed to obtain resource basic information (??????????????????????????????)");
        }

        return Message.messageToResponse(message);
    }


    @GET
    @Path("getResourceInfo")
    public Response getResourceInfo(@Context HttpServletRequest request, @QueryParam("resourceId") String resourceId){
        return Message.messageToResponse(Message.ok("Obtained information successfully(??????????????????)"));
    }


}
