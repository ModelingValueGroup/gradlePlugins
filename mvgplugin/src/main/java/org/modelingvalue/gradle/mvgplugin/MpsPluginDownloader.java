//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
//                                                                                                                     ~
// Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
// compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
// Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
// an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
// specific language governing permissions and limitations under the License.                                          ~
//                                                                                                                     ~
// Maintainers:                                                                                                        ~
//     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
// Contributors:                                                                                                       ~
//     Arjan Kok, Carel Bast                                                                                           ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.gradle.mvgplugin;

import static org.modelingvalue.gradle.mvgplugin.Info.DEVELOP_CHANNEL;
import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;
import static org.modelingvalue.gradle.mvgplugin.Info.MASTER_CHANNEL;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.internal.plugins.PluginApplicationException;
import org.jetbrains.annotations.Nullable;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.IntellijUpdateMetadata;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.PluginBean;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.PluginId;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.PluginRepository;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.PluginUpdateBean;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.PluginXmlBean;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.PluginXmlId;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.ProductEnum;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.UpdateBean;
import org.modelingvalue.gradle.mvgplugin.JetBrainsPluginRepoRestApi.UpdateId;

public class MpsPluginDownloader {
    public static final String                                       PLUGIN_MAIN_SEP    = ";";
    public static final String                                       PLUGIN_SUB_SEP     = "|";
    private final       Map<String, String>                          branchToChannelMap = new HashMap<>();
    private final       Map<String, Map<PluginXmlId, PluginXmlBean>> channelMap         = new HashMap<>();
    private final       JetBrainsPluginRepoRestApi                   pluginRepo         = new JetBrainsPluginRepoRestApi();


    public MpsPluginDownloader() {
        branchToChannelMap.put("master", MASTER_CHANNEL);
        branchToChannelMap.put("develop", DEVELOP_CHANNEL);
    }

    public Stream<Path> downloadAllPlugins(Path mpsInstallDir, String pluginList, String mpsBuildNumber, String channel) {
        branchToChannelMap.put(channel, channel);
        return Arrays.stream(pluginList.split(Util.makeSplitterPattern(PLUGIN_MAIN_SEP)))
                .flatMap(plugin -> downloadPlugin(mpsInstallDir, mpsBuildNumber, channel, plugin));
    }

    private Stream<Path> downloadPlugin(Path mpsInstallDir, String mpsBuildNumber, String channel, String xmlIdAndDetail) {
        String[]    parts       = xmlIdAndDetail.split(Util.makeSplitterPattern(PLUGIN_SUB_SEP), 2);
        String      detail      = parts.length == 1 || parts[1].isBlank() ? null : parts[1];
        PluginXmlId pluginXmlId = new PluginXmlId(parts[0]);
        PluginId    pluginId    = getNumIdFromXmlId(pluginXmlId);

        LOGGER.info("+ mvg-mps: download plugin '{}' (id={} build={} channel={}) to '{}'", xmlIdAndDetail, pluginId, mpsBuildNumber, channel, mpsInstallDir);

        if (pluginId == null) {
            LOGGER.info("+ mvg-mps: plugin '{}' not found for MPS", xmlIdAndDetail);
            throw new IllegalArgumentException("MPS plugin " + xmlIdAndDetail + " not found");
        }
        UpdateId updateId = findUpdateId(detail, mpsBuildNumber, pluginXmlId, pluginId, channel);
        if (updateId == null) {
            LOGGER.info("+ mvg-mps: plugin '{}' not found for MPS build '{}'", xmlIdAndDetail, mpsBuildNumber);
            throw new IllegalArgumentException("MPS plugin " + xmlIdAndDetail + " not found for " + mpsBuildNumber);
        }
        return actualDownload(mpsInstallDir, pluginXmlId, pluginId, updateId);
    }

    @Nullable
    private UpdateId findUpdateId(String detail, String mpsBuildNumber, PluginXmlId pluginXmlId, PluginId pluginId, String channel) {
        UpdateId updateId;
        if (detail == null) {
            updateId = getUpdateFromXmlIdAndChannel(mpsBuildNumber, pluginId, pluginXmlId, "");
            if (updateId != null) {
                LOGGER.info("+ mvg-mps: plugin '{}' found (updateId={} channel='')", pluginXmlId, updateId);
                return updateId;
            }
            LOGGER.info("+ mvg-mps: plugin '{}' not found (channel='')", updateId);
            return null;
        }
        if (detail.equals("BRANCHED")) {
            if (channel == null) {
                channel = MASTER_CHANNEL;
            }
            boolean isMain = MASTER_CHANNEL.equals(channel);
            boolean isEap  = DEVELOP_CHANNEL.equals(channel);

            if (!isMain && !isEap) {
                updateId = getUpdateFromXmlIdAndChannel(mpsBuildNumber, pluginId, pluginXmlId, channel);
                if (updateId != null) {
                    LOGGER.info("+ mvg-mps: BRANCHED plugin '{}' found (updateId={} channel={})", pluginXmlId, updateId, channel);
                    return updateId;
                }
                LOGGER.info("+ mvg-mps: BRANCHED plugin '{}' not found (channel={})", pluginXmlId, channel);
            }
            if (!isMain) {
                updateId = getUpdateFromXmlIdAndChannel(mpsBuildNumber, pluginId, pluginXmlId, DEVELOP_CHANNEL);
                if (updateId != null) {
                    LOGGER.info("+ mvg-mps: BRANCHED plugin '{}' found (updateId={} channel={}})", pluginXmlId, updateId, DEVELOP_CHANNEL);
                    return updateId;
                }
                LOGGER.info("+ mvg-mps: BRANCHED plugin '{}' not found (channel={})", pluginXmlId, DEVELOP_CHANNEL);
            }
            updateId = getUpdateFromXmlIdAndChannel(mpsBuildNumber, pluginId, pluginXmlId, MASTER_CHANNEL);
            if (updateId != null) {
                LOGGER.info("+ mvg-mps: BRANCHED plugin '{}' found (updateId={} channel={}})", pluginXmlId, updateId, MASTER_CHANNEL);
                return updateId;
            }
            LOGGER.info("+ mvg-mps: BRANCHED plugin '{}' not found (channel={}})", pluginXmlId, MASTER_CHANNEL);
            return updateId;
        } else {
            updateId = getUpdateFromXmlIdAndChannel(mpsBuildNumber, pluginId, pluginXmlId, detail);
            if (updateId != null) {
                LOGGER.info("+ mvg-mps: plugin '{}' found (updateId={} channel={}) (detail as channel)", pluginXmlId, updateId, detail);
                return updateId;
            }
            updateId = getUpdateIdFromXmlIdAndVersion(pluginId, detail);
            if (updateId != null) {
                LOGGER.info("+ mvg-mps: plugin '{}' found (updateId={} version={}) (detail as version)", pluginXmlId, updateId, detail);
                return updateId;
            }
            LOGGER.info("+ mvg-mps: plugin '{}' not found (version={} or channel={})", pluginXmlId, detail, detail);
            return null;
        }
    }

    private Stream<Path> actualDownload(Path mpsInstallDir, PluginXmlId pluginXmlId, PluginId pluginId, UpdateId updateId) {
        PluginUpdateBean updateBean = getUpdateBean(updateId);
        File             toFile     = mpsInstallDir.resolve(pluginXmlId + ".zip").toFile();
        LOGGER.info("+ mvg-mps: DOWNLOADING: xmlid={} pluginid={} updateid={} version={} channel={} compat={} since-until={} => {}", pluginXmlId, pluginId, updateId, updateBean.version, updateBean.channel, updateBean.compatibleVersions.get(ProductEnum.MPS), updateBean.sinceUntil, toFile);
        long size = download(updateId, toFile);
        LOGGER.info("+ mvg-mps: downloaded {} bytes", size);

        Set<String> dependents = getDependents(pluginId, updateId);
        LOGGER.info("+ mvg-mps: plugin {} has {} plugin DEPENDENCIES: {} (TODO: also download them)", pluginXmlId, dependents.size(), dependents);

        return Stream.of(toFile.toPath());
    }

    private UpdateId getUpdateIdFromXmlIdAndVersion(PluginId pluginId, String version) {
        PluginBean pluginBean = pluginRepo.getPluginById(pluginId);
        UpdateBean ub         = pluginRepo.getPluginVersions(pluginId).stream().filter(b -> b.version.equals(version)).findAny().map(it -> new UpdateBean(it.id, pluginBean.id, pluginBean.xmlId, it.version, it.channel)).orElse(null);
        return ub == null ? null : ub.id;
    }

    private PluginId getNumIdFromXmlId(PluginXmlId pluginXmlId) {
        PluginBean pb = pluginRepo.getPluginByXmlId(pluginXmlId);
        return pb == null ? null : pb.id;
    }

    public UpdateId getUpdateFromXmlIdAndChannel(String mpsBuildNumber, PluginId pluginId, PluginXmlId pluginXmlId, String branchOrChannel) {
        Map<PluginXmlId, PluginXmlBean> channelMap = getFromChannelMap(mpsBuildNumber, branchOrChannel);
        if (channelMap == null) {
            return null;
        }
        PluginXmlBean pluginXmlBean = channelMap.get(pluginXmlId);
        if (pluginXmlBean == null) {
            return null;
        }
        return getUpdateIdFromXmlIdAndVersion(pluginId, pluginXmlBean.version);
    }

    private Map<PluginXmlId, PluginXmlBean> getFromChannelMap(String mpsBuildNumber, String branchOrChannel) {
        String channel = branchToChannelMap.getOrDefault(branchOrChannel, branchOrChannel);
        return channelMap.computeIfAbsent(mpsBuildNumber + "@@@" + channel, __ -> {
            PluginRepository pluginRepository = pluginRepo.listPlugins(mpsBuildNumber, channel);
            return pluginRepository.categories == null ? new HashMap<>() : pluginRepository.categories
                    .stream()
                    .flatMap(cat -> cat.plugins.stream().map(x -> new PluginXmlBean(
                            x.name,
                            x.id,
                            x.version,
                            cat.name,
                            x.ideaVersion.sinceBuild,
                            x.ideaVersion.untilBuild,
                            x.vendor,
                            x.depends
                    )))
                    .collect(Collectors.toMap(b -> b.id, b -> b));
        });
    }

    private long download(UpdateId updateId, File toFile) {
        try {
            return pluginRepo.download(updateId, toFile);
        } catch (PluginApplicationException e) {
            LOGGER.error("unable to download MPS plugin with updateId {}", updateId);
            return -1;
        }
    }

    private PluginUpdateBean getUpdateBean(UpdateId updateId) {
        return pluginRepo.getUpdateById(updateId);
    }

    private Set<String> getDependents(PluginId pluginId, UpdateId updateId) {
        IntellijUpdateMetadata meta = pluginRepo.getIntellijUpdateMetadata(pluginId, updateId);
        return meta == null ? Set.of() : meta.dependencies;
    }
}
