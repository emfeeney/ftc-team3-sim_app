using NUnit.Framework;
using UnityEditor;
using UnityEngine;

namespace VI_Arena.Tests
{
    /// <summary>
    /// The art is imported small, as manager decision D35 (option B) chose: people's and skies' textures at
    /// most 1024 px, every other texture at most 2048 px, crunched DXT (skies BC6H). These read the imported
    /// textures themselves, so they fail if the import scripts stop applying the settings.
    /// </summary>
    public class SmallBuildTests
    {
        private const string Root = "Assets/VI_Arena/Resources/";

        private static T Imported<T>(string path) where T : Texture
        {
            T texture = AssetDatabase.LoadAssetAtPath<T>(Root + path);
            Assert.IsNotNull(texture, path + " is missing");
            return texture;
        }

        /// <summary>WHAT: A person's texture is at most 1024 px and crunched.</summary>
        [Test]
        public void PeoplesTexturesAreCrunchedAt1024()
        {
            Texture2D diffuse = Imported<Texture2D>("People/ch08/textures/Ch08_1001_Diffuse.jpg");
            Assert.LessOrEqual(Mathf.Max(diffuse.width, diffuse.height), 1024);
            Assert.IsTrue(diffuse.format == TextureFormat.DXT1Crunched || diffuse.format == TextureFormat.DXT5Crunched,
                "format " + diffuse.format);
        }

        /// <summary>WHAT: Each sky is at most 1024 px a face and compressed as HDR (BC6H).</summary>
        [TestCase("Settings/competition-hall/sky.hdr")]
        [TestCase("Settings/night-stadium/sky.hdr")]
        [TestCase("Settings/moon-base/sky.exr")]
        public void SkiesAreBc6hAt1024(string path)
        {
            Cubemap sky = Imported<Cubemap>(path);
            Assert.LessOrEqual(sky.width, 1024);
            Assert.AreEqual(TextureFormat.BC6H, sky.format);
        }

        /// <summary>WHAT: Other setting art (Earth was 8192 before) is at most 2048 px and crunched.</summary>
        [Test]
        public void OtherTexturesAreCrunchedAtMost2048()
        {
            Texture2D earth = Imported<Texture2D>("Settings/moon-base/earth.jpg");
            Assert.LessOrEqual(Mathf.Max(earth.width, earth.height), 2048);
            Assert.Greater(Mathf.Max(earth.width, earth.height), 1024, "not shrunk like the people");
            Assert.IsTrue(earth.format == TextureFormat.DXT1Crunched || earth.format == TextureFormat.DXT5Crunched,
                "format " + earth.format);
        }
    }
}
