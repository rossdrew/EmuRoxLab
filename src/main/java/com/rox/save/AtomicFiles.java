package com.rox.save;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Replacing a file all at once - for anything that mustn't be lost to a failed or interrupted write. */
public final class AtomicFiles {
    private AtomicFiles(){
    }

    /**
     * Writes {@code content} to {@code target} without ever leaving a torn/partial file behind, even on
     * a crash mid-write. The temp file's content is forced to the storage device ({@link FileChannel#force}) before
     * the atomic rename, so a crash or power loss after this method returns can't lose or corrupt what
     * was just written. This does not separately sync the containing directory's own metadata (no
     * portable Java API for that) - an exceedingly narrow window where a literal power loss in the
     * instant right after the rename could still show the previous file on some filesystems, distinct
     * from (and far rarer than) the crash/kill/disk-full scenarios this guards against.
     */
    public static void write(final Path target, final byte[] content) throws IOException {
        final Path folder = target.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        //its own staging file, so two writers of the same target (e.g. two windows saving one ROM) can't swap halves
        final Path tmp = Files.createTempFile(folder, target.getFileName() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)){
                final ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()){
                    channel.write(buffer);
                }
                //XXX force()'s effect (data genuinely reaching the storage device) isn't observable from a
                //test without simulating a real power loss - accepted gap, same category as
                //SpeakerAudioOutput.openDefaultLine()'s own real-hardware sliver
                channel.force(true);
            }
            Files.move(tmp, target.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e){
            Files.deleteIfExists(tmp);
            throw e;
        }
    }
}
