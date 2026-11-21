using UnityEditor;

namespace VI_Arena.Editor
{
    /// <summary>
    /// The settings that keep the built app small (manager decision D35, option B of UnityBot's size test
    /// C26.5): the app went from about 462 MB to about 173 MB with no visible difference in the test views.
    /// <list type="bullet">
    ///   <item>Textures: compressed, crunched (DXT; skies, which are HDR, use BC6H without crunch).</item>
    ///   <item>The people's and the skies' textures at most 1024 px, every other texture at most 2048 px.</item>
    ///   <item>Models: mesh compression (smaller vertex numbers; every triangle is kept).</item>
    ///   <item>The build itself: LZ4HC (<see cref="BuildWindowsApp"/>).</item>
    /// </list>
    /// The import scripts (<see cref="SettingArtImport"/>, <see cref="PeopleImport"/>,
    /// <see cref="FieldModelImport"/>) call this, so new art gets the same settings.
    /// </summary>
    public static class SmallBuild
    {
        /// <summary>Largest size of a person's or a sky's texture, in pixels.</summary>
        public const int PeopleAndSkyMaxPixels = 1024;

        /// <summary>Largest size of any other texture, in pixels.</summary>
        public const int OtherMaxPixels = 2048;

        /// <summary>How much the models' vertex numbers are compressed.</summary>
        public const ModelImporterMeshCompression MeshCompression = ModelImporterMeshCompression.Medium;

        /// <summary>
        /// Bump this whenever these settings change: the import scripts return it from GetVersion, so Unity
        /// re-imports the art with the new settings.
        /// </summary>
        public const uint ImportVersion = 2;

        /// <summary>Compresses a texture and limits its size to <paramref name="maxPixels"/>.</summary>
        public static void Compress(TextureImporter importer, int maxPixels, bool hdr)
        {
            importer.maxTextureSize = maxPixels;
            importer.textureCompression = TextureImporterCompression.Compressed;
            importer.crunchedCompression = !hdr; // crunch has no HDR format
            importer.compressionQuality = 50;
        }

        /// <summary>True for a 360° HDR picture (a sky).</summary>
        public static bool IsHdr(string assetPath)
        {
            string path = assetPath.ToLowerInvariant();
            return path.EndsWith(".hdr") || path.EndsWith(".exr");
        }
    }
}
