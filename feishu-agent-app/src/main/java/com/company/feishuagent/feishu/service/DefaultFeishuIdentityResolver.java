package com.company.feishuagent.feishu.service;

import com.company.feishuagent.feishu.model.EnterpriseIdentity;
import com.company.feishuagent.persistence.entity.FeishuUserProfileEntity;
import com.company.feishuagent.persistence.repo.FeishuUserProfileRepository;
import com.lark.oapi.Client;
import com.lark.oapi.service.contact.v3.model.GetUserReq;
import com.lark.oapi.service.contact.v3.model.GetUserResp;
import com.lark.oapi.service.contact.v3.model.User;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DefaultFeishuIdentityResolver implements FeishuIdentityResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultFeishuIdentityResolver.class);

    private final Client openApiClient;
    private final FeishuUserProfileRepository profileRepository;

    public DefaultFeishuIdentityResolver(
            @Value("${feishu.websocket.app-id:}") String appId,
            @Value("${feishu.websocket.app-secret:}") String appSecret,
            FeishuUserProfileRepository profileRepository) {
        this.openApiClient = Client.newBuilder(appId, appSecret).build();
        this.profileRepository = profileRepository;
    }

    @Override
    public EnterpriseIdentity resolve(String openId) {
        if (openId == null || openId.isBlank()) {
            return null;
        }
        FeishuUserProfileEntity cached = profileRepository.findByOpenId(openId);
        if (cached != null && isComplete(cached)) {
            return toIdentity(cached);
        }
        return resolveFromApi(openId, "open_id");
    }

    @Override
    public EnterpriseIdentity resolveByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        FeishuUserProfileEntity cached = profileRepository.findByUserId(userId);
        if (cached != null && isComplete(cached)) {
            return toIdentity(cached);
        }
        return resolveFromApi(userId, "user_id");
    }

    private EnterpriseIdentity resolveFromApi(String lookupId, String userIdType) {
        User user = fetchUser(lookupId, userIdType);
        if (user == null) {
            logger.warn("feishu_identity_resolve_failed lookupId={} type={}", lookupId, userIdType);
            return null;
        }
        EnterpriseIdentity identity = toIdentity(user);
        cacheProfile(identity);
        logger.info(
                "feishu_identity_resolved openId={} namePresent={} mobilePresent={} employeeNoPresent={}",
                identity.openId(),
                identity.name() != null,
                identity.hasMobile(),
                identity.hasEmployeeNo());
        return identity;
    }

    private User fetchUser(String lookupId, String userIdType) {
        try {
            GetUserReq request = GetUserReq.newBuilder()
                    .userIdType(userIdType)
                    .departmentIdType("open_department_id")
                    .userId(lookupId)
                    .build();
            GetUserResp response = openApiClient.contact().v3().user().get(request);
            if (response != null && response.success() && response.getData() != null && response.getData().getUser() != null) {
                return response.getData().getUser();
            }
            return null;
        } catch (Exception ex) {
            logger.warn("feishu_fetch_user_failed lookupId={} type={} reason={}", lookupId, userIdType, ex.getMessage());
            return null;
        }
    }

    private void cacheProfile(EnterpriseIdentity identity) {
        if (!identity.isComplete()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        profileRepository.upsert(new FeishuUserProfileEntity(
                identity.openId(),
                identity.unionId(),
                identity.userId(),
                identity.name(),
                identity.mobile(),
                identity.employeeNo(),
                identity.email(),
                now, now, now));
    }

    private EnterpriseIdentity toIdentity(FeishuUserProfileEntity cached) {
        return new EnterpriseIdentity(
                cached.actorOpenId(),
                cached.actorUnionId(),
                cached.actorUserId(),
                cached.actorName(),
                cached.actorMobile(),
                cached.actorEmployeeNo(),
                cached.actorEmail(),
                null);
    }

    private EnterpriseIdentity toIdentity(User user) {
        return new EnterpriseIdentity(
                blankToNull(user.getOpenId()),
                blankToNull(user.getUnionId()),
                blankToNull(user.getUserId()),
                blankToNull(user.getName()),
                normalizeMobile(user.getMobile()),
                blankToNull(user.getEmployeeNo()),
                blankToNull(user.getEmail()),
                null);
    }

    private boolean isComplete(FeishuUserProfileEntity cached) {
        return cached.actorOpenId() != null && !cached.actorOpenId().isBlank()
                && cached.actorMobile() != null && !cached.actorMobile().isBlank()
                && cached.actorEmployeeNo() != null && !cached.actorEmployeeNo().isBlank();
    }

    private String normalizeMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        String trimmed = mobile.trim();
        if (trimmed.startsWith("+86") && trimmed.length() > 3) {
            return trimmed.substring(3);
        }
        return trimmed;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
