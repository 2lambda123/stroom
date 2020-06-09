/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import { NavLink } from "react-router-dom";
import { Credentials } from "components/authentication/types";
import useForm from "react-hook-form";
import { Button } from "antd";
import { OptionalRequiredFieldMessage } from "../FormComponents/FormComponents";
import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import FormField from "../ChangePassword2/FormField";
import PasswordField from "../ChangePassword2/PasswordField";
import { UserOutlined, LockOutlined } from "@ant-design/icons";

interface FormData {
  email: string;
  password: string;
}

export const InputContainer: React.FunctionComponent<{
  label: string;
  children: any;
  error: boolean;
}> = ({ label, children, error }) => {
  return (
    <div className="SignIn__input-container">
      <div className="SignIn__label">{label}:</div>
      {children}
      <OptionalRequiredFieldMessage visible={error} />
    </div>
  );
};

// export const PasswordInput: React.FunctionComponent<{
//   name: string;
//   placeholder: string;
//   onChange?: ChangeEventHandler<HTMLInputElement>;
// }> = ({ name, placeholder, onChange }) => {
//   return (
//     <Input.Password
//       name={name}
//       placeholder={placeholder}
//       prefix={<LockOutlined style={{ color: "rgba(0,0,0,.25)" }} />}
//       onChange={onChange}
//     />
//   );
// };

const SignInForm: React.FunctionComponent<{
  onSubmit: (credentials: Credentials) => void;
  isSubmitting: boolean;
  allowPasswordResets?: boolean;
}> = ({ onSubmit, allowPasswordResets, isSubmitting }) => {
  const {
    triggerValidation,
    setValue,
    register,
    handleSubmit,
    // getValues,
    errors,
  } = useForm<FormData>({
    defaultValues: {
      email: "",
      password: "",
    },
    mode: "onChange",
  });

  React.useEffect(() => {
    register({ name: "email", type: "custom" }, { required: true });
    register({ name: "password", type: "custom" }, { required: true });
  }, [register]);

  const handleInputChange = async (
    name: "email" | "password",
    value: string,
  ) => {
    setValue(name, value);
    await triggerValidation({ name });
  };

  // ensures that field contains characters
  const fieldRequired = (label: string, value: string) => {
    if (value.length === 0) {
      // if required and is empty, add required error to state
      throw new Error(`${label} is required`);
    } else {
      const regex = /^.+$/i;
      if (!regex.test(value)) throw new Error("Field required");
    }
  };

  return (
    <LogoPage>
      <FormContainer>
        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="SignIn__content">
            <div className="SignIn__icon-container">
              <img
                src={require("../../images/infinity_logo.svg")}
                alt="Stroom logo"
              />
            </div>

            <FormField
              type="text"
              fieldId="email"
              label="User Name"
              placeholder="Enter User Name"
              className="no-icon-padding left-icon-padding hide-background-image"
              validator={fieldRequired}
              onStateChanged={async e => handleInputChange("email", e.value)}
              leftIcon={<UserOutlined />}
              validateOnLoad={true}
            />

            <PasswordField
              fieldId="password"
              label="Password"
              placeholder="Enter Password"
              className="left-icon-padding right-icon-padding hide-background-image"
              validator={fieldRequired}
              onStateChanged={async e => handleInputChange("password", e.value)}
              leftIcon={<LockOutlined />}
            />

            <div className="SignIn__actions page__buttons Button__container">
              <Button
                className="SignIn__button"
                type="primary"
                loading={isSubmitting}
                htmlType="submit"
                ref={register}
              >
                Sign In
              </Button>
            </div>

            {allowPasswordResets ? (
              <NavLink
                className="SignIn__reset-password"
                to={"/s/resetPasswordRequest"}
              >
                Forgot password?
              </NavLink>
            ) : (
              undefined
            )}
          </div>
        </form>
      </FormContainer>
    </LogoPage>
  );
};

export default SignInForm;
