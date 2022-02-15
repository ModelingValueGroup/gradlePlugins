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

import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.modelingvalue.json.Json;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamConverter;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;

/*

    @ = not yet implemented because not needed right now

    @   uploadNewPlugin             : POST /api/plugins/{family}/upload           family:ProductFamily          cid:int                 file:body           licenseUrl:body  => PluginBean
        getPluginByXmlId            : GET  /api/plugins/{family}/{pluginXmlId}    family:ProductFamily          pluginXmlId:PluginXmlId                                      => PluginBean
        getPluginById               : GET  /api/plugins/{id}                      id:PluginId                                                                                => PluginBean
    @   getPluginDevelopers         : GET  /api/plugins/{id}/developers           id:PluginId                                                                                => List<PluginUserBean>
    @   getPluginChannels           : GET  /api/plugins/{id}/channels             id:PluginId                                                                                => List<String>
    @   getPluginCompatibleProducts : GET  /api/plugins/{id}/compatible-products  id:PluginId                                                                                => List<ProductEnum>
    @   getUpdatesByVersionAndFamily: GET  /api/plugins/{id}/updates              id:String                     version:String          family:ProductFamily                 => List<PluginUpdateBean>
    @   getPluginXmlIdByDependency  : GET  /api/plugins                           dependency:String             includeOptional:bool                                         => List<String>
    @   searchPluginsXmlIds         : GET  /api/search                            build:String                  max:int                 offset:int          search:String    => List<String>
    @   searchUpdates               : GET  /api/search/updates                    build:String                  pluginXMLId:PluginXmlId                                      => List<UpdateBean>
    @   searchLastCompatibleUpdate  : POST /api/search/compatibleUpdates          body:CompatibleUpdateRequest                                                               => List<UpdateBean>
        getUpdateById               : GET  /api/updates/{id}                      id:PluginId                                                                                => PluginUpdateBean
        getPluginVersions           : GET  /api/plugins/{id}/updateVersions       id:PluginId                                                                                => List<PluginUpdateVersion>
        getIntelliJUpdateMetaData   : GET  /files/{pluginId}/{updateId}/meta.json pluginId:PluginId             updateId:UpdateId                                            => IntellijUpdateMetadata
        getPluginsXmlIds            : GET  /files/pluginsXMLIds.json                                                                                                         => List<String>
    @   download                    : GET  /plugin/download                       pluginId:PluginId             version:String          ?channel:String                      => ResponseBody
        download                    : GET  /plugin/download                       updateId:UpdateId                                                                          => ResponseBody
    @   upload                      : POST /plugin/uploadPlugin                   pluginId:PluginId             channel:body            notes:body          file:body        => PluginUpdateBean
    @   uploadByXmlId               : POST /plugin/uploadPlugin                   xmlId:body                    channel:body            notes:body          file:body        => PluginUpdateBean
        listPlugins                 : GET  /plugins/list                          build:String                  ?channel:String         ?pluginId:PluginId                   => PluginRepository
    @   downloadCompatiblePlugin    : GET  /pluginManager?action=download         id:String                     build:String            ?channel:String                      => ResponseBody

*/
public class JetBrainsPluginRepoRestApi {
    public static final  String DEFAULT_REPO                = "https://plugins.jetbrains.com";
    private static final Type   LIST_OF_STRINGS             = EXTENDS_LIST_OF_STRINGS.class.getGenericInterfaces()[0];
    private static final Type   LIST_OF_PluginUpdateVersion = EXTENDS_LIST_OF_PluginUpdateVersion.class.getGenericInterfaces()[0];

    interface EXTENDS_LIST_OF_STRINGS extends List<String> {
    }

    interface EXTENDS_LIST_OF_PluginUpdateVersion extends List<PluginUpdateVersion> {
    }

    private final String  url;
    private final XStream xStreamXml;

    public JetBrainsPluginRepoRestApi() {
        this(DEFAULT_REPO);
    }

    public JetBrainsPluginRepoRestApi(String url) {
        this.url = url;
        xStreamXml = getXStreamForXml();
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // API implementation
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public PluginBean getPluginByXmlId(PluginXmlId pluginXmlId) {
        return getPluginByXmlId(ProductFamily.INTELLIJ, pluginXmlId);
    }

    public PluginBean getPluginByXmlId(ProductFamily family, PluginXmlId pluginXmlId) {
        URI url = new ReplacingURIBuilder(this.url, "/api/plugins/{family}/{pluginXmlId}")
                .add("family", family)
                .add("pluginXmlId", pluginXmlId)
                .build();
        return get(url, PluginBean.class);
    }

    public PluginBean getPluginById(PluginId id) {
        URI url = new ReplacingURIBuilder(this.url, "/api/plugins/{id}")
                .add("id", id)
                .build();
        return get(url, PluginBean.class);
    }

    public PluginUpdateBean getUpdateById(UpdateId id) {
        URI url = new ReplacingURIBuilder(this.url, "/api/updates/{id}")
                .add("id", id)
                .build();
        return get(url, PluginUpdateBean.class);
    }

    public List<PluginUpdateVersion> getPluginVersions(PluginId id) {
        URI url = new ReplacingURIBuilder(this.url, "/api/plugins/{id}/updateVersions")
                .add("id", id)
                .build();
        return get(url, LIST_OF_PluginUpdateVersion);
    }

    public IntellijUpdateMetadata getIntellijUpdateMetadata(PluginId pluginId, UpdateId updateId) {
        URI url = new ReplacingURIBuilder(this.url, "/files/{pluginId}/{updateId}/meta.json")
                .add("pluginId", pluginId)
                .add("updateId", updateId)
                .build();
        return get(url, IntellijUpdateMetadata.class);
    }

    public List<String> getPluginsXmlIds() {
        URI url = new ReplacingURIBuilder(this.url, "/files/pluginsXMLIds.json").build();
        return get(url, LIST_OF_STRINGS);
    }

    public long download(UpdateId updateId, File toFile) {
        URI url = new ReplacingURIBuilder(this.url, "/plugin/download")
                .add("updateId", updateId)
                .build();
        byte[] content = get(url, null);
        if (content == null) {
            throw new IllegalArgumentException("can't download " + updateId + ", url returned nothing");
        }
        try {
            Files.createDirectories(toFile.toPath().toAbsolutePath().getParent());
            Files.write(toFile.toPath(), content);
            return Files.size(toFile.toPath());
        } catch (IOException e) {
            throw new IllegalArgumentException("can not write contents of zip file " + toFile, e);
        }
    }

    public PluginRepository listPlugins(String build, String channel) {
        return listPlugins(build, channel, null);
    }

    public PluginRepository listPlugins(String build, String channel, PluginId pluginId) {
        URI url = new ReplacingURIBuilder(this.url, "/plugins/list")
                .add("build", build)
                .add("channel", channel)
                .add("pluginId", pluginId)
                .build();
        return get(url, PluginRepository.class);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // UTILS
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @NotNull
    public static XStream getXStreamForXml() {
        XStream x = new XStream();
        x.addPermission(NoTypePermission.NONE);
        x.addPermission(NullPermission.NULL);
        x.addPermission(PrimitiveTypePermission.PRIMITIVES);
        x.allowTypeHierarchy(Collection.class);
        x.allowTypes(JetBrainsPluginRepoRestApi.class.getDeclaredClasses());
        x.processAnnotations(JetBrainsPluginRepoRestApi.class.getDeclaredClasses());
        return x;
    }

    private static class ReplacingURIBuilder {
        private final URIBuilder builder;

        public ReplacingURIBuilder(String url, String path) {
            try {
                builder = new URIBuilder(url).setPath(path);
            } catch (URISyntaxException e) {
                throw new Error("can't make the uri for JetBrainsPluginRepoRestApi.getPluginByXmlId()", e);
            }
        }

        public ReplacingURIBuilder add(String name, Object value) {
            String  varName     = "{" + name + "}";
            boolean blank       = value == null || value.toString().isBlank();
            String  stringValue = blank ? null : value.toString();
            if (builder.getPathSegments().stream().anyMatch(s -> s.equals(varName))) {
                if (blank) {
                    throw new NullPointerException("the url parameter named " + varName + " can not be null or blank");
                }
                builder.setPathSegments(builder.getPathSegments().stream().map(s -> s.equals(varName) ? stringValue : s).collect(Collectors.toList()));
            } else if (!blank) {
                builder.addParameter(name, stringValue);
            }
            return this;
        }

        public URI build() {
            try {
                return builder.build();
            } catch (URISyntaxException e) {
                throw new Error("can't make the uri for JetBrainsPluginRepoRestApi.getPluginByXmlId()", e);
            }
        }
    }

    @Nullable
    private <T> T get(URI uri, Type type) {
        return call("GET", uri, type);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> T call(@SuppressWarnings("SameParameterValue") String method, URI uri, Type type) {
        try {
            URL               url = uri.toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method);
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            con.setRequestProperty("Accept", "application/json, text/xml");
            con.setConnectTimeout(10_000);
            con.setReadTimeout(10_000);
            con.connect();
            String status   = con.getHeaderField(null);
            String encoding = con.getContentType();
            byte[] bytes    = con.getInputStream().readAllBytes();
            String text     = new String(bytes, StandardCharsets.UTF_8);
            LOGGER.info("+ mvg-mps-api: URL={}", url);
            LOGGER.trace("++ mvg-mps-api: STATUS   = {}", status);
            LOGGER.trace("++ mvg-mps-api: ENCODING = {}", encoding);
            con.getHeaderFields().entrySet().stream().filter(e -> e.getKey() != null).sorted(Map.Entry.comparingByKey()).forEach(e -> LOGGER.trace("++ mvg-mps-api: +++ {} = {}", String.format("%-25s", e.getKey()), e.getValue()));
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("++ mvg-mps-api: +++++++++++++++++++++");
                LOGGER.trace("++ mvg-mps-api: " + text.substring(0, Math.min(text.length(), 30_000)).replace("\n", "\\n").replace("\r", "\\r"));
                LOGGER.trace("++ mvg-mps-api: +++++++++++++++++++++");
            }
            if (!isOk(status)) {
                throw new Error("REST call " + url + " returned error status '" + status + "'");
            }
            if (isXmlEncoding(encoding)) {
                Object o = xStreamXml.fromXML(text);
                LOGGER.trace("++ mvg-mps-api: read XML : {}", o);
                return (T) o;
            }
            if (isJsonEncoding(encoding)) {
                Object o = Json.fromJson(type, text);
                LOGGER.trace("++ mvg-mps-api: read JSON: {}", o);
                return (T) o;
            }
            if (isZipEncoding(encoding)) {
                LOGGER.trace("++ mvg-mps-api: read ZIP : len={}", text.length());
                return (T) bytes;
            }
            throw new Error("can't parse encoding '" + encoding + "' from " + url);
        } catch (IOException e) {
            throw new Error("bad url in JetBrainsPluginRepoRestApi: " + uri, e);
        }
    }

    private boolean isOk(String status) {
        return Arrays.asList(status.split(" ")).contains("200");
    }

    private boolean isXmlEncoding(String encoding) {
        List<String> parts = Arrays.asList(encoding.split(";"));
        return parts.contains("text/xml") && parts.contains("charset=UTF-8");
    }

    private boolean isJsonEncoding(String encoding) {
        List<String> parts = Arrays.asList(encoding.split(";"));
        return parts.contains("application/json");
    }

    private boolean isZipEncoding(String encoding) {
        List<String> parts = Arrays.asList(encoding.split(";"));
        return parts.contains("application/zip");
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // DATA TYPES
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static class PluginIdConverter implements SingleValueConverter {
        public String toString(Object obj) {
            return "" + ((PluginId) obj).value;
        }

        public Object fromString(String s) {
            return new PluginId(Integer.parseInt(s));
        }

        public boolean canConvert(Class type) {
            return type.equals(PluginId.class);
        }
    }

    public static class PluginXmlIdConverter implements SingleValueConverter {
        public String toString(Object obj) {
            return "" + ((PluginXmlId) obj).value;
        }

        public Object fromString(String s) {
            return new PluginXmlId(s);
        }

        public boolean canConvert(Class type) {
            return type.equals(PluginXmlId.class);
        }
    }

    public static class UpdateIdConverter implements SingleValueConverter {
        public String toString(Object obj) {
            return "" + ((UpdateId) obj).value;
        }

        public Object fromString(String s) {
            return new UpdateId(Integer.parseInt(s));
        }

        public boolean canConvert(Class type) {
            return type.equals(UpdateId.class);
        }
    }

    @XStreamConverter(PluginIdConverter.class)
    public static class PluginId {
        public final int value;

        public PluginId(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PluginId that = (PluginId) o;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    @XStreamConverter(PluginXmlIdConverter.class)
    public static class PluginXmlId {
        public final String value;

        public PluginXmlId(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PluginXmlId that = (PluginXmlId) o;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    @XStreamConverter(UpdateIdConverter.class)
    public static class UpdateId {
        public final int value;

        public UpdateId(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            UpdateId that = (UpdateId) o;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    public static class PluginUpdateVersion {
        UpdateId id;
        String   version;
        String   channel; // opt
    }

    public static class PluginXmlBean {
        String           name;
        PluginXmlId      id;
        String           version;
        String           category;
        String           sinceBuild; // OPTIONAL
        String           untilBuild; // OPTIONAL
        PluginVendorBean vendor; // OPTIONAL
        List<String>     depends;

        public PluginXmlBean(String name, PluginXmlId id, String version, String category, String sinceBuild, String untilBuild, PluginVendorBean vendor, List<String> depends) {
            this.name = name;
            this.id = id;
            this.version = version;
            this.category = category;
            this.sinceBuild = sinceBuild;
            this.untilBuild = untilBuild;
            this.vendor = vendor;
            this.depends = depends;
        }
    }

    public static class UpdateBean {
        final UpdateId    id;
        final PluginId    pluginId;
        final PluginXmlId pluginXmlId;
        final String      version;
        final String      channel; // OPTIONAL

        public UpdateBean(UpdateId id, PluginId pluginId, PluginXmlId pluginXmlId, String version, String channel) {
            this.id = id;
            this.pluginId = pluginId;
            this.pluginXmlId = pluginXmlId;
            this.version = version;
            this.channel = channel;
        }

        public UpdateBean(UpdateId id, PluginId pluginId, PluginXmlId pluginXmlId, String version) {
            this(id, pluginId, pluginXmlId, version, null);
        }

        public UpdateBean channel(String channel) {
            return new UpdateBean(id, pluginId, pluginXmlId, version, channel);
        }
    }

    @SuppressWarnings("unused")
    public static class PluginUpdateBean {
        UpdateId                 id;
        String                   link;
        String                   version; // OPTIONAL
        boolean                  approve; // OPTIONAL
        boolean                  listed; // OPTIONAL
        boolean                  recalculateCompatibilityAllowed; // OPTIONAL
        String                   cdate; // OPTIONAL
        String                   file; // OPTIONAL
        String                   downloadUrl; // OPTIONAL
        String                   notes; // OPTIONAL
        String                   since; // OPTIONAL
        String                   until; // OPTIONAL
        String                   sinceUntil; // OPTIONAL
        String                   channel; // OPTIONAL
        int                      size; // OPTIONAL
        int                      downloads; // OPTIONAL
        PluginId                 pluginId;
        Map<ProductEnum, String> compatibleVersions; // OPTIONAL
        PluginUserBean           author; // OPTIONAL
        Set<String>              modules; // OPTIONAL
        String                   description;
    }

    @XStreamAlias("PluginBean")
    public static class PluginBean {
        public PluginId               id;
        public String                 name;
        public PluginXmlId            xmlId;
        public String                 description; // OPTIONAL
        public String                 preview; // OPTIONAL
        public String                 docText; // OPTIONAL
        public String                 email; // OPTIONAL
        public String                 family;
        public ProductFamily          family_;
        public String                 copyright; // OPTIONAL
        public int                    downloads;
        public PluginPurchaseInfoBean purchaseInfo; // OPTIONAL
        public PluginVendorBean       vendor; // OPTIONAL
        public PluginURLsBean         urls;
        @XStreamConverter(ListPluginTagBeanConverter.class)
        public List<PluginTagBean>    tags; // OPTIONAL
        public Set<IntellijThemeBean> themes; // OPTIONAL
        public String                 icon; // OPTIONAL
        public String                 programmingLanguage; // OPTIONAL
        public String                 language; // OPTIONAL
        public String                 link; // OPTIONAL
        public boolean                approve; // OPTIONAL
        public boolean                customIdeList; // OPTIONAL
        public long                   cdate;
        public boolean                hasUnapprovedUpdate; // OPTIONAL
        public String                 pricingModel; // OPTIONAL
        public List<URLBean>          screens;
    }

    @SuppressWarnings("unused")
    public static class URLBean {
        String url;
    }

    public static class IntellijThemeBean {
        public String  name;
        public Boolean dark;
    }

    @SuppressWarnings("unused")
    public static class PluginPurchaseInfoBean {
        public String productCode;
        public String buyUrl; // OPTIONAL
        public String purchaseTerms; // OPTIONAL
    }

    public static class ListPluginTagBeanConverter extends CollectionConverter {
        public ListPluginTagBeanConverter(Mapper mapper) {
            super(mapper);
        }

        public ListPluginTagBeanConverter(Mapper mapper, Class type) {
            super(mapper, type);
        }

        @Override
        public boolean canConvert(Class type) {
            return true;
        }

        @Override
        protected Object readBareItem(HierarchicalStreamReader reader, UnmarshallingContext context, Object current) {
            return context.convertAnother(current, PluginTagBean.class);
        }
    }

    public static class PluginTagBean {
        public PluginId            id;
        public String              name;
        public String              link;
        public List<ProductFamily> families; // OPTIONAL
        public boolean             privileged;
        public boolean             searchable;
    }

    @SuppressWarnings("unused")
    public static class PluginURLsBean {
        public String url; // OPTIONAL
        public String forumUrl; // OPTIONAL
        public String licenseUrl; // OPTIONAL
        public String bugtrackerUrl; // OPTIONAL
        public String docUrl; // OPTIONAL
        public String sourceCodeUrl; // OPTIONAL
    }

    @XStreamAlias("plugin-repository")
    public static class PluginRepository {
        @XStreamOmitField
        public String                ff;
        @XStreamImplicit(itemFieldName = "category")
        public List<XmlCategoryBean> categories;
    }

    @XStreamAlias("category")
    public static class XmlCategoryBean {
        @XStreamAsAttribute
        public String              name; // OPTIONAL
        @XStreamImplicit(itemFieldName = "idea-plugin")
        public List<XmlPluginBean> plugins; // OPTIONAL
    }

    @XStreamAlias("idea-plugin")
    public static class XmlPluginBean {
        public String             name;
        public PluginXmlId        id;
        public String             version;
        @XStreamAlias("idea-version")
        public XmlIdeaVersionBean ideaVersion; // OPTIONAL
        public PluginVendorBean   vendor; // OPTIONAL
        @XStreamImplicit(itemFieldName = "depends")
        public List<String>       depends; // OPTIONAL
        public String             description;
        public double             rating;
        @XStreamAlias("change-notes")
        public String             changeNotes;
        @XStreamImplicit(itemFieldName = "tags")
        public List<String>       tags;
        public String             productCode;
        @XStreamAsAttribute
        public int                downloads;
        @XStreamAsAttribute
        public long               size;
        @XStreamAsAttribute
        public long               date;
        @XStreamAsAttribute
        public long               updatedDate;
        @XStreamAsAttribute
        public String             url;
        @XStreamAlias("download-url")
        public String             downloadUrl;
    }

    public static class XmlIdeaVersionBean {
        @XStreamAsAttribute
        public String min;
        @XStreamAsAttribute
        public String max;
        @XStreamAsAttribute
        @XStreamAlias("since-build")
        public String sinceBuild; // OPTIONAL
        @XStreamAsAttribute
        @XStreamAlias("until-build")
        public String untilBuild; // OPTIONAL
    }

    public static class PluginUserBean {
        public PluginXmlId id;
        public String      name;
        public String      link;
        public String      hubLogin; // OPTIONAL
        public boolean     isJetBrains;
        public String      icon;
    }

    public static class IntellijUpdateMetadata {
        public UpdateId     id;
        public PluginXmlId  xmlId;
        public String       name;
        public String       description;
        public List<String> tags;
        public String       vendor;
        public String       version;
        public String       notes;
        public Set<String>  dependencies;
        public Set<String>  optionalDependencies;
        public String       since; // OPTIONAL
        public String       until; // OPTIONAL
        public String       productCode; // OPTIONAL
        public String       sourceCodeUrl; // OPTIONAL
        public String       url; // OPTIONAL
        public int          size;
        public String       organization;
    }

    @SuppressWarnings("unused")
    @XStreamConverter(value = ToAttributedValueConverter.class, strings = {"name"})
    public static class PluginVendorBean {
        public String  name;
        public String  email;
        public String  url; // OPTIONAL
        public int     totalPlugins;
        public int     totalUsers;
        public String  link;
        public String  publicName;
        public String  countryCode;
        public String  country;
        public String  city;
        public String  street;
        public String  description;
        public boolean isVerified;
        public int     vendorId;

        public PluginVendorBean() {
        }

        public PluginVendorBean(String name, String email, String url) {
            this.name = name;
            this.email = email;
            this.url = url;
        }
    }

    public enum ProductFamily {
        TEAMCITY("teamcity"),
        INTELLIJ("intellij"),
        HUB("hub"),
        EDU("edu"),
        KTOR("ktor"),
        DOTNET("dotnet");

        final String id;

        ProductFamily(String id) {
            this.id = id;
        }
    }

    public enum ProductEnum {
        IDEA("idea", "IntelliJ IDEA", "intellij-idea", "IU", null, "Ultimate", null, ProductFamily.INTELLIJ),
        IDEA_COMMUNITY("idea_ce", "IntelliJ IDEA", "intellij-idea", "IC", null, "Community", IDEA, ProductFamily.INTELLIJ),
        IDEA_EDUCATIONAL("idea_edu", "IntelliJ IDEA", "intellij-idea", "IE", null, "Educational", IDEA, ProductFamily.INTELLIJ),
        TIDE("t_ide", "T-IDE", null, "TD", null, null, null, ProductFamily.INTELLIJ),
        PHPSTORM("phpstorm", "PhpStorm", null, "PS", null, null, null, ProductFamily.INTELLIJ),
        WEBSTORM("webstorm", "WebStorm", null, "WS", null, null, null, ProductFamily.INTELLIJ),
        PYCHARM("pycharm", "PyCharm", "pycharm", "PY", "PYA", "Professional", null, ProductFamily.INTELLIJ),
        PYCHARM_COMMUNITY("pycharm_ce", "PyCharm", "pycharm", "PC", "PCA", "Community", PYCHARM, ProductFamily.INTELLIJ),
        PYCHARM_EDUCATIONAL("pycharm_edu", "PyCharm", "pycharm-edu", "PE", null, "Educational", PYCHARM, ProductFamily.INTELLIJ),
        RUBYMINE("ruby", "RubyMine", "rubymine", "RM", null, null, null, ProductFamily.INTELLIJ),
        APPCODE("objc", "AppCode", "appcode", "OC", null, null, null, ProductFamily.INTELLIJ),
        CLION("clion", "CLion", null, "CL", null, null, null, ProductFamily.INTELLIJ),
        GOLAND("go", "GoLand", "gogland", "GO", null, null, null, ProductFamily.INTELLIJ),
        DBE("dbe", "DataGrip", "datagrip", "DB", null, null, null, ProductFamily.INTELLIJ),
        GATEWAY("gateway", "JetBrains Gateway", null, "CWMG", null, null, null, ProductFamily.INTELLIJ),
        RIDER("rider", "Rider", null, "RD", "RDCPPP", null, null, ProductFamily.INTELLIJ),
        RESHARPER("resharper", "ReSharper", "resharper", "RS", null, null, null, ProductFamily.DOTNET),
        MPS("mps", "MPS", null, "MPS", null, null, null, ProductFamily.INTELLIJ),
        ANDROID_STUDIO("androidstudio", "Android Studio", null, "AI", null, null, null, ProductFamily.INTELLIJ),
        TEAMCITY("teamcity", "TeamCity", "teamcity", "TC", null, null, null, ProductFamily.TEAMCITY),
        EDU_PLUGIN("edu_plugin", "Educational plugin", "edu", "EDU", null, null, null, ProductFamily.EDU),
        HUB("hub", "Hub", "hub", "HUB", null, null, null, ProductFamily.HUB),
        YOUTRACK("youtrack", "YouTrack", "youtrack", "YT", null, null, null, ProductFamily.HUB),
        UPSOURCE("upsource", "Upsource", "upsource", "UP", null, null, null, ProductFamily.HUB);

        final String        id;
        final String        title;
        final String        logo;
        final String        code;
        final List<String>  alternativeCodes;
        final String        edition;
        final ProductEnum   parent;
        final ProductFamily family;

        ProductEnum(String id, String title, String logo, String code, String alternativeCode, String edition, ProductEnum parent, ProductFamily family) {
            this.id = id;
            this.title = title;
            this.logo = logo != null ? logo : id;
            this.code = code;
            this.alternativeCodes = alternativeCode != null ? List.of(alternativeCode) : List.of();
            this.edition = edition;
            this.parent = parent;
            this.family = family;
        }
    }
}
