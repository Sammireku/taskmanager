const functions = require("firebase-functions");
const admin = require("firebase-admin");
const express = require("express");
const cors = require("cors");
const axios = require("axios");
const { GoogleGenerativeAI } = require("@google/generative-ai");

admin.initializeApp();

const app = express();
app.use(cors({ origin: true }));
app.use(express.json());

// Secure Environment Variable Access for API Keys
const GEMINI_API_KEY = process.env.GEMINI_API_KEY || functions.config().gemini?.key || "";
const PLACES_API_KEY = process.env.PLACES_API_KEY || functions.config().places?.key || "";

/**
 * Proxy Endpoint for Gemini AI Text Generation
 * Handles natural language task extraction & schedule suggestions securely on backend.
 */
app.post("/gemini/generate", async (req, res) => {
  try {
    const { prompt } = req.body;
    if (!prompt) {
      return res.status(400).json({ error: "Missing 'prompt' in request body." });
    }

    if (!GEMINI_API_KEY) {
      return res.status(500).json({ error: "GEMINI_API_KEY not configured on cloud server." });
    }

    const genAI = new GoogleGenerativeAI(GEMINI_API_KEY);
    const model = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });
    const result = await model.generateContent(prompt);
    const responseText = result.response.text();

    return res.json({ text: responseText });
  } catch (error) {
    console.error("Gemini Proxy Error:", error);
    return res.status(500).json({ error: error.message || "Failed to query Gemini API." });
  }
});

/**
 * Proxy Endpoint for Google Places Autocomplete Search
 */
app.get("/places/autocomplete", async (req, res) => {
  try {
    const { input, lat, lng } = req.query;
    if (!input) {
      return res.status(400).json({ error: "Missing 'input' parameter." });
    }

    if (!PLACES_API_KEY) {
      return res.status(500).json({ error: "PLACES_API_KEY not configured on cloud server." });
    }

    let url = `https://maps.googleapis.com/maps/api/place/autocomplete/json?input=${encodeURIComponent(input)}&key=${PLACES_API_KEY}`;
    if (lat && lng) {
      url += `&location=${lat},${lng}&radius=10000`;
    }

    const response = await axios.get(url);
    return res.json(response.data);
  } catch (error) {
    console.error("Places Autocomplete Proxy Error:", error);
    return res.status(500).json({ error: error.message || "Failed to query Google Places API." });
  }
});

/**
 * Proxy Endpoint for Google Place Details
 */
app.get("/places/details", async (req, res) => {
  try {
    const { place_id } = req.query;
    if (!place_id) {
      return res.status(400).json({ error: "Missing 'place_id' parameter." });
    }

    if (!PLACES_API_KEY) {
      return res.status(500).json({ error: "PLACES_API_KEY not configured on cloud server." });
    }

    const url = `https://maps.googleapis.com/maps/api/place/details/json?place_id=${place_id}&fields=name,formatted_address,geometry&key=${PLACES_API_KEY}`;
    const response = await axios.get(url);
    return res.json(response.data);
  } catch (error) {
    console.error("Place Details Proxy Error:", error);
    return res.status(500).json({ error: error.message || "Failed to query Place Details API." });
  }
});

exports.api = functions.https.onRequest(app);
