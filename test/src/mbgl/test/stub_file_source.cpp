#include <mbgl/storage/resource_options.hpp>
#include <mbgl/test/stub_file_source.hpp>
#include <mbgl/util/async_request.hpp>
#include <mbgl/util/client_options.hpp>

namespace mbgl {

using namespace std::chrono_literals;

class StubFileRequest : public AsyncRequest {
public:
    StubFileRequest(StubFileSource& fileSource_)
        : fileSource(fileSource_) {}

    ~StubFileRequest() override { fileSource.remove(this); }

    StubFileSource& fileSource;
};

StubFileSource::StubFileSource(ResponseType type_)
    : StubFileSource::StubFileSource(
          ResourceOptions().withTileServerOptions(TileServerOptions::MapTilerConfiguration()), ClientOptions(), type_) {
}

StubFileSource::StubFileSource(const ResourceOptions& resourceOptions_,
                               const ClientOptions& clientOptions_,
                               ResponseType type_)
    : type(type_),
      resourceOptions(resourceOptions_.clone()),
      clientOptions(clientOptions_.clone()) {
    if (type == ResponseType::Synchronous) {
        return;
    }

    timer.start(1ms, 1ms, [this] {
        // Explicit copy to avoid iterator invalidation if ~StubFileRequest gets called within the loop.
        auto pending_ = pending;
        for (auto& pair : pending_) {
            std::optional<Response> res = std::get<1>(pair.second)(std::get<0>(pair.second));
            if (res) {
                // This must be before calling the callback, because it's
                // possible that the callback could:
                //
                //   1. Deallocate the AsyncRequest itself, thus removing it
                //   from pending
                //   2. Allocate a new AsyncRequest at the same memory location
                //
                // If remove(pair.first) was called after both those things
                // happened, it would remove the newly allocated request rather
                // than the intended request.
                if (!res->error) {
                    remove(pair.first);
                }

                std::get<2>(pair.second)(*res);
            }
        }
    });
}

StubFileSource::~StubFileSource() = default;

std::unique_ptr<AsyncRequest> StubFileSource::request(const Resource& resource,
                                                      std::function<void(Response)> callback) {
    auto req = std::make_unique<StubFileRequest>(*this);
    if (type == ResponseType::Synchronous) {
        std::optional<Response> res = response(resource);
        if (res) {
            callback(*res);
        }
    } else {
        pending.emplace(req.get(), std::make_tuple(resource, response, std::move(callback)));
    }
    return req;
}

void StubFileSource::remove(AsyncRequest* req) {
    auto it = pending.find(req);
    if (it != pending.end()) {
        pending.erase(it);
    }
}

void StubFileSource::setProperty(const std::string& key, const mapbox::base::Value& value) {
    properties[key] = value;
}

mapbox::base::Value StubFileSource::getProperty(const std::string& key) const {
    auto it = properties.find(key);
    return (it != properties.end()) ? it->second : mapbox::base::Value();
}

std::optional<Response> StubFileSource::defaultResponse(const Resource& resource) {
    switch (resource.kind) {
        case Resource::Kind::Style:
            if (!styleResponse) throw std::runtime_error("unexpected style request");
            return styleResponse(resource);
        case Resource::Kind::Source:
            if (!sourceResponse) throw std::runtime_error("unexpected source request");
            return sourceResponse(resource);
        case Resource::Kind::Tile:
            if (!tileResponse) throw std::runtime_error("unexpected tile request");
            return tileResponse(resource);
        case Resource::Kind::Glyphs:
            if (!glyphsResponse) throw std::runtime_error("unexpected glyphs request");
            return glyphsResponse(resource);
        case Resource::Kind::SpriteJSON:
            if (!spriteJSONResponse) throw std::runtime_error("unexpected sprite JSON request");
            return spriteJSONResponse(resource);
        case Resource::Kind::SpriteImage:
            if (!spriteImageResponse) throw std::runtime_error("unexpected sprite image request");
            return spriteImageResponse(resource);
        case Resource::Kind::Image:
            if (!imageResponse) throw std::runtime_error("unexpected image request");
            return imageResponse(resource);
        case Resource::Kind::Unknown:
            throw std::runtime_error("unknown resource type");
    }

    // The above switch is exhaustive, but placate GCC nonetheless:
    return Response();
}

void StubFileSource::setResourceOptions(ResourceOptions options) {
    resourceOptions = options;
}

ResourceOptions StubFileSource::getResourceOptions() {
    return resourceOptions.clone();
}

void StubFileSource::setClientOptions(ClientOptions options) {
    clientOptions = options;
}

ClientOptions StubFileSource::getClientOptions() {
    return clientOptions.clone();
}

} // namespace mbgl
