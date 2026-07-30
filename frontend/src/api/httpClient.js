import axios from "axios";

const httpClient = axios.create({
  baseURL: "/",
  headers: {
    "Content-Type": "application/json",
  },
});

export default httpClient;

