#include "asset_manager_file_source.hpp"

#include <mbgl/platform/settings.hpp>
#include <mbgl/storage/file_source_request.hpp>
#include <mbgl/storage/resource.hpp>
#include <mbgl/storage/resource_options.hpp>
#include <mbgl/storage/response.hpp>
#include <mbgl/util/thread.hpp>
#include <mbgl/util/url.hpp>
#include <mbgl/util/util.hpp>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

namespace mbgl {

class AssetManagerFileSource::Impl {
public:
    Impl(ActorRef<Impl>,
         AAssetManager* assetManager_,
         const ResourceOptions resourceOptions_,
         const ClientOptions clientOptions_)
        : resourceOptions(resourceOptions_.clone()),
          clientOptions(clientOptions_.clone()),
          assetManager(assetManager_) {}

    void request(const std::string& url, ActorRef<FileSourceRequest> req) {
        // Note: AssetManager already prepends "assets" to the filename.
        const std::string path = mbgl::util::percentDecode(url.substr(8));

        Response response;

        if (AAsset* asset = AAssetManager_open(assetManager, path.c_str(), AASSET_MODE_BUFFER)) {
            response.data = std::make_shared<std::string>(reinterpret_cast<const char*>(AAsset_getBuffer(asset)),
                                                          AAsset_getLength64(asset));
            AAsset_close(asset);
        } else {
            response.error = std::make_unique<Response::Error>(Response::Error::Reason::NotFound,
                                                               "Could not read asset");
        }

        req.invoke(&FileSourceRequest::setResponse, response);
    }

    void setResourceOptions(ResourceOptions options) { resourceOptions = options; }

    ResourceOptions getResourceOptions() { return resourceOptions.clone(); }

    void setClientOptions(ClientOptions options) { clientOptions = options; }

    ClientOptions getClientOptions() { return clientOptions.clone(); }

private:
    AAssetManager* assetManager;
    ResourceOptions resourceOptions;
    ClientOptions clientOptions;
};

AssetManagerFileSource::AssetManagerFileSource(jni::JNIEnv& env,
                                               const jni::Object<android::AssetManager>& assetManager_,
                                               const ResourceOptions resourceOptions,
                                               const ClientOptions clientOptions)
    : assetManager(jni::NewGlobal<jni::EnvAttachingDeleter>(env, assetManager_)),
      impl(std::make_unique<util::Thread<Impl>>(
          util::makeThreadPrioritySetter(platform::EXPERIMENTAL_THREAD_PRIORITY_FILE),
          "AssetManagerFileSource",
          AAssetManager_fromJava(&env, jni::Unwrap(assetManager.get())),
          resourceOptions.clone(),
          clientOptions.clone())) {}

AssetManagerFileSource::~AssetManagerFileSource() = default;

std::unique_ptr<AsyncRequest> AssetManagerFileSource::request(const Resource& resource,
                                                              std::function<void(Response)> callback) {
    auto req = std::make_unique<FileSourceRequest>(std::move(callback));

    impl->actor().invoke(&Impl::request, resource.url, req->actor());

    return std::move(req);
}

bool AssetManagerFileSource::canRequest(const Resource& resource) const {
    return resource.url.starts_with(mbgl::util::ASSET_PROTOCOL);
}

void AssetManagerFileSource::setResourceOptions(ResourceOptions options) {
    impl->actor().invoke(&Impl::setResourceOptions, options.clone());
}

ResourceOptions AssetManagerFileSource::getResourceOptions() {
    return impl->actor().ask(&Impl::getResourceOptions).get();
}

void AssetManagerFileSource::setClientOptions(ClientOptions options) {
    impl->actor().invoke(&Impl::setClientOptions, options.clone());
}

ClientOptions AssetManagerFileSource::getClientOptions() {
    return impl->actor().ask(&Impl::getClientOptions).get();
}

} // namespace mbgl
