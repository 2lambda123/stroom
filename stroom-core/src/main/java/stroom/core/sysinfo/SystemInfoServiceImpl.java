package stroom.core.sysinfo;

import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.HasSystemInfo.ParamInfo;
import stroom.util.sysinfo.SystemInfoResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;

// If we make it a singleton due to systemInfoSuppliers then we would
// probably need the injected set to be a map of providers instead.
public class SystemInfoServiceImpl implements SystemInfoService {

    private final Map<String, HasSystemInfo> systemInfoSuppliers;
    private final SecurityContext securityContext;

    @Inject
    public SystemInfoServiceImpl(final Set<HasSystemInfo> systemInfoSuppliers,
                                 final SecurityContext securityContext) {
        this.systemInfoSuppliers = systemInfoSuppliers.stream()
                .collect(Collectors.toMap(HasSystemInfo::getSystemInfoName, Function.identity()));
        this.securityContext = securityContext;
    }

    @Override
    public List<SystemInfoResult> getAll() {
        checkPermission();
        // We should have a user in context as this is coming from an authenticated rest api
        return systemInfoSuppliers.values()
                .stream()
                .map(HasSystemInfo::getSystemInfo)
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> getNames() {
        checkPermission();
        return systemInfoSuppliers.keySet();
    }

    @Override
    public List<ParamInfo> getParamInfo(final String providerName) {
        checkPermission();
        final HasSystemInfo systemInfoSupplier = Objects.requireNonNull(
                systemInfoSuppliers.get(providerName),
                () -> LogUtil.message("Unknown system info provider name [{}]", providerName));
        return systemInfoSupplier.getParamInfo();
    }

    @Override
    public Optional<SystemInfoResult> get(final String providerName) {
        return get(providerName, Collections.emptyMap());
    }

    @Override
    public Optional<SystemInfoResult> get(final String providerName, final Map<String, String> params) {
        checkPermission();

        // We should have a user in context as this is coming from an authenticated rest api
        final HasSystemInfo systemInfoSupplier = systemInfoSuppliers.get(providerName);

        return NullSafe.getAsOptional(
                systemInfoSupplier,
                supplier -> supplier.getSystemInfo(params));
    }

    private void checkPermission() {
        if (!securityContext.hasAppPermission(PermissionNames.VIEW_SYSTEM_INFO_PERMISSION)) {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to view system information"
            );
        }
    }
}
