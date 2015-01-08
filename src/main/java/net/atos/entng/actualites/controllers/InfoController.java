package net.atos.entng.actualites.controllers;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.atos.entng.actualites.filters.ThreadFilter;
import net.atos.entng.actualites.services.InfoService;
import net.atos.entng.actualites.services.impl.InfoServiceSqlImpl;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.request.RequestUtils;

public class InfoController extends ControllerHelper {

	private static final String THREAD_ID_PARAMETER = "id";
	private static final String INFO_ID_PARAMETER = "infoid";
	private static final String RESULT_SIZE_PARAMETER = "resultSize";

	private static final String SCHEMA_INFO_CREATE = "createInfo";
	private static final String SCHEMA_INFO_UPDATE = "updateInfo";

	// TRASH: 1; DRAFT: 1; PENDING: 2; PUBLISHED: 3
	private static final List<Integer> status_list = new ArrayList<Integer>(Arrays.asList(0, 1, 2, 3));

	protected final InfoService infoService;

	public InfoController(){
		this.infoService = new InfoServiceSqlImpl();
	}

	@Get("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+ INFO_ID_PARAMETER)
	@ApiDoc("Retrieve : retrieve an Info in thread by thread and by id")
	@SecuredAction("info.read")
	public void getInfo(final HttpServerRequest request) {
		// TODO IMPROVE @SecuredAction : Security on Info as a resource
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				infoService.retrieve(infoId, user, notEmptyResponseHandler(request));
			}
		});
	}

	@Get("/infos")
	@ApiDoc("Get infos.")
	@SecuredAction("info.list")
	public void listInfos(final HttpServerRequest request) {
		// TODO IMPROVE : Security on Infos visibles by statuses / dates is not enforced
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(UserInfos user) {
				infoService.list(user, arrayResponseHandler(request));
			}
		});
	}

	@Get("/thread/:"+THREAD_ID_PARAMETER+"/infos")
	@ApiDoc("Get infos in thread by thread id.")
	@SecuredAction("info.list")
	public void listInfosByThreadId(final HttpServerRequest request) {
		// TODO IMPROVE : Security on Infos visibles by statuses / dates is not enforced
		final String threadId = request.params().get(THREAD_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(UserInfos user) {
				infoService.listByThreadId(threadId, user, arrayResponseHandler(request));
			}
		});
	}

	@Get("/linker/infos")
	@ApiDoc("List infos without their content. Used by linker")
	@SecuredAction("info.list")
	public void listInfosForLinker(final HttpServerRequest request) {
		// TODO IMPROVE : Security on Infos visibles by statuses / dates is not enforced
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(UserInfos user) {
				infoService.listForLinker(user, arrayResponseHandler(request));
			}
		});
	}

	@Get("/infos/last/:" + RESULT_SIZE_PARAMETER)
	@ApiDoc("Get infos in thread by status and by thread id.")
	@SecuredAction("info.list")
	public void listLastPublishedInfos(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(UserInfos user) {
				String resultSize = request.params().get(RESULT_SIZE_PARAMETER);
				int size;
				if (resultSize == null || resultSize.trim().isEmpty()) {
					badRequest(request);
					return;
				}
				else {
					try {
						size = Integer.parseInt(resultSize);
					} catch (NumberFormatException e) {
						badRequest(request, "actualites.widget.bad.request.size.must.be.an.integer");
						return;
					}

					if(size <=0 || size > 20) {
						badRequest(request, "actualites.widget.bad.request.invalid.size");
						return;
					}
				}
				infoService.listLastPublishedInfos(user, size, new Handler<Either<String, JsonArray>>() {
					@Override
					public void handle(Either<String, JsonArray> event) {
						if (event.isRight()) {
							JsonArray rightValue = event.right().getValue();
							if (rightValue != null && rightValue.size() > 0) {
								JsonObject result = new JsonObject();
								result.putArray("result", rightValue);
								renderJson(request, result, 200);
							} else {
								notFound(request);
							}
						} else {
							badRequest(request, event.left().getValue());
						}
					}
				});
			}
		});
	}

	@Post("/thread/:" + THREAD_ID_PARAMETER + "/info")
	@ApiDoc("Add a new Info with draft status")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.contrib", type = ActionType.RESOURCE)
	public void createDraft(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				RequestUtils.bodyToJson(request, pathPrefix + SCHEMA_INFO_CREATE, new Handler<JsonObject>() {
					@Override
					public void handle(JsonObject resource) {
						resource.putNumber("status", status_list.get(1));
						crudService.create(resource, user, notEmptyResponseHandler(request));
					}
				});
			}
		});
	}

	@Post("/thread/:" + THREAD_ID_PARAMETER + "/info/pending")
	@ApiDoc("Add a new Info with pending status")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.contrib", type = ActionType.RESOURCE)
	public void createPending(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				RequestUtils.bodyToJson(request, pathPrefix + SCHEMA_INFO_CREATE, new Handler<JsonObject>() {
					@Override
					public void handle(JsonObject resource) {
						resource.putNumber("status", status_list.get(2));
						crudService.create(resource, user, notEmptyResponseHandler(request));
					}
				});
			}
		});
	}

	@Post("/thread/:" + THREAD_ID_PARAMETER + "/info/published")
	@ApiDoc("Add a new Info published status")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.publish", type = ActionType.RESOURCE)
	public void createPublished(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				RequestUtils.bodyToJson(request, pathPrefix + SCHEMA_INFO_CREATE, new Handler<JsonObject>() {
					@Override
					public void handle(JsonObject resource) {
						resource.putNumber("status", status_list.get(3));
						crudService.create(resource, user, notEmptyResponseHandler(request));
					}
				});
			}
		});
	}

	@Put("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+INFO_ID_PARAMETER+"/draft")
	@ApiDoc("Update : update an Info in Draft state in thread by thread and by id")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.contrib", type = ActionType.RESOURCE)
	public void updateDraft(final HttpServerRequest request) {
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				RequestUtils.bodyToJson(request, pathPrefix + SCHEMA_INFO_UPDATE, new Handler<JsonObject>() {
					@Override
					public void handle(JsonObject resource) {
						Integer status = resource.getInteger("status");
						if(!status_list.contains(status) || status != status_list.get(1)){
							crudService.update(infoId, resource, user, notEmptyResponseHandler(request));
						}
					}
				});
			}
		});
	}

	@Put("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+ INFO_ID_PARAMETER +"/pending")
	@ApiDoc("Update : update an Info in Draft state in thread by thread and by id")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.publish", type = ActionType.RESOURCE)
	public void updatePending(final HttpServerRequest request) {
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				RequestUtils.bodyToJson(request, pathPrefix + SCHEMA_INFO_UPDATE, new Handler<JsonObject>() {
					@Override
					public void handle(JsonObject resource) {
						Integer status = resource.getInteger("status");
						if(!status_list.contains(status) || status != status_list.get(2)){
							crudService.update(infoId, resource, user, notEmptyResponseHandler(request));
						}
					}
				});
			}
		});
	}

	@Put("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+ INFO_ID_PARAMETER +"/published")
	@ApiDoc("Update : update an Info in Draft state in thread by thread and by id")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.publish", type = ActionType.RESOURCE)
	public void updatePublished(final HttpServerRequest request) {
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				RequestUtils.bodyToJson(request, pathPrefix + SCHEMA_INFO_UPDATE, new Handler<JsonObject>() {
					@Override
					public void handle(JsonObject resource) {
						Integer status = resource.getInteger("status");
						if(!status_list.contains(status) || status != status_list.get(3)){
							crudService.update(infoId, resource, user, notEmptyResponseHandler(request));
						}
					}
				});
			}
		});
	}

	@Override
	@Delete("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+ INFO_ID_PARAMETER)
	@ApiDoc("Delete : Real delete an Info in thread by thread and by id")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.manager", type = ActionType.RESOURCE)
	public void delete(final HttpServerRequest request) {
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				crudService.delete(infoId, user, notEmptyResponseHandler(request));
			}
		});
	}

	@Put("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+ INFO_ID_PARAMETER +"/submit")
	@ApiDoc("Submit : Change an Info to Pending state in thread by thread and by id")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.contrib", type = ActionType.RESOURCE)
	public void submit(final HttpServerRequest request) {
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				JsonObject resource = new JsonObject();
				resource.putNumber("status", status_list.get(2));
				crudService.update(infoId, resource, user, notEmptyResponseHandler(request));
			}
		});
	}

	@Put("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+ INFO_ID_PARAMETER +"/unsubmit")
	@ApiDoc("Cancel Submit : Change an Info to Draft state in thread by thread and by id")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.contrib", type = ActionType.RESOURCE)
	public void unsubmit(final HttpServerRequest request) {
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				JsonObject resource = new JsonObject();
				resource.putNumber("status", status_list.get(1));
				crudService.update(infoId, resource, user, notEmptyResponseHandler(request));
			}
		});
	}

	@Put("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+ INFO_ID_PARAMETER +"/publish")
	@ApiDoc("Publish : Change an Info to Published state in thread by thread and by id")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.publish", type = ActionType.RESOURCE)
	public void publish(final HttpServerRequest request) {
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				JsonObject resource = new JsonObject();
				resource.putNumber("status", status_list.get(3));
				crudService.update(infoId, resource, user, notEmptyResponseHandler(request));
			}
		});
	}

	@Put("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+ INFO_ID_PARAMETER +"/unpublish")
	@ApiDoc("Unpublish : Change an Info to Draft state in thread by thread and by id")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.publish", type = ActionType.RESOURCE)
	public void unpublish(final HttpServerRequest request) {
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				JsonObject resource = new JsonObject();
				resource.putNumber("status", status_list.get(2));
				crudService.update(infoId, resource, user, notEmptyResponseHandler(request));
			}
		});
	}

	@Put("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+ INFO_ID_PARAMETER +"/thrash")
	@ApiDoc("Trash : Change an Info to Trash state in thread by thread and by id")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.contrib", type = ActionType.RESOURCE)
	public void trash(final HttpServerRequest request) {
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				JsonObject resource = new JsonObject();
				resource.putNumber("status", status_list.get(0));
				crudService.update(infoId, resource, user, notEmptyResponseHandler(request));
			}
		});
	}

	@Put("/thread/:"+THREAD_ID_PARAMETER+"/info/:"+ INFO_ID_PARAMETER +"restore")
	@ApiDoc("Cancel Trash : Change an Info to Draft state in thread by thread and by id")
	@ResourceFilter(ThreadFilter.class)
	@SecuredAction(value = "thread.contrib", type = ActionType.RESOURCE)
	public void restore(final HttpServerRequest request) {
		final String infoId = request.params().get(INFO_ID_PARAMETER);
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				JsonObject resource = new JsonObject();
				resource.putNumber("status", status_list.get(1));
				crudService.update(infoId, resource, user, notEmptyResponseHandler(request));
			}
		});
	}

}
