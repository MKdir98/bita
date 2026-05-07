package ir.bita.esm.service.handler;

import ir.bita.esm.service.command.CreateServiceCollectionCommand;
import ir.bita.esm.service.entity.ServiceCollection;
import ir.bita.esm.service.repository.ServiceCollectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handler for CreateServiceCollectionCommand.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateServiceCollectionHandler {

    private final ServiceCollectionRepository repository;

    @Transactional
    public ServiceCollection handle(CreateServiceCollectionCommand command) {
        // Validate unique name
        if (repository.existsByNameAndDeletedFalse(command.getName())) {
            throw new IllegalArgumentException("Collection with name '" + command.getName() + "' already exists");
        }

        // Validate unique base path
        if (repository.existsByBasePathAndDeletedFalse(command.getBasePath())) {
            throw new IllegalArgumentException("Collection with base path '" + command.getBasePath() + "' already exists");
        }

        // Create collection
        ServiceCollection collection = ServiceCollection.builder()
                .name(command.getName())
                .basePath(command.getBasePath())
                .description(command.getDescription())
                .build();

        collection = repository.save(collection);

        log.info("Created service collection: {} (ID: {})", collection.getName(), collection.getId());
        return collection;
    }
}
